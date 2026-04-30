package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Application.Auth;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Order.Commands.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.OptimisticLockException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PurchasingService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.purchasing");

    private final IActiveOrderRepository activeOrderRepository;
    private final IOrderHistoryRepository orderHistoryRepository;
    private final EventManagementService eventManagementService;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final Auth auth;

    public PurchasingService(IActiveOrderRepository activeOrderRepository,
            IOrderHistoryRepository orderHistoryRepository,
            EventManagementService eventManagementService,
            IPaymentAPI paymentGateway,
            ITicketSupplyAPI ticketProvider,
            Auth auth) {
        if (activeOrderRepository == null || orderHistoryRepository == null || eventManagementService == null
                || paymentGateway == null || ticketProvider == null || auth == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.activeOrderRepository = activeOrderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
        this.eventManagementService = eventManagementService;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
        this.auth = auth;
    }

    @Transactional
    public UUID createActiveOrder(String token, UUID eventId) {
        try {
            UUID userId = requireValidUser(token);
            
            requireEventIsValid(eventId);
            requireOrderIsUniqueForUser(userId, eventId);

            UUID orderId = UUID.randomUUID();

            ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId);

            activeOrderRepository.save(activeOrder);

            AUDIT.info("op=createActiveOrder order={} user={} event={} result=ok",
                    orderId, userId, eventId);

            return orderId;

        } 
        catch (DataIntegrityViolationException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason=concurrent_order", eventId);
            throw new IllegalStateException("User already has an active order for this event", e);
        }
        catch (RuntimeException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason={}",
                    eventId,
                    e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void addSeatsToExistingOrder(String token, AddSeatsToActiveOrderCommand cmd) {
        ActiveOrder activeOrder = null;

        try {
            requireAddSeatsToActiveOrderCommand(cmd);

            UUID userId = requireValidUser(token);
            activeOrder = requireOwnedModifiableOrderForUpdate(cmd.orderId(), userId);

            addSeatsToActiveOrder(activeOrder, cmd.seatIds());

            AUDIT.info(
                    "op=addSeatsToExistingOrder order={} user={} event={} area={} holdToken={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    cmd.areaId(),
                    activeOrder.getOrderId());

        } catch (TimeExpiredException e) {
            expireAndSave(activeOrder);
            AUDIT.warn(
                    "op=addSeatsToExistingOrder order={} result=expired reason={}",
                    cmd != null ? cmd.orderId() : null,
                    e.getMessage());

            throw e;

        } catch (RuntimeException e) {
            AUDIT.warn(
                    "op=addSeatsToExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null,
                    e.getMessage());

            throw e;
        }
    }

    @Transactional
    public void removeSeatsFromExistingOrder(String token, RemoveSeatsFromActiveOrderCommand cmd) {
        ActiveOrder activeOrder = null;

        try {
            requireRemoveSeatsFromActiveOrderCommand(cmd);

            UUID userId = requireValidUser(token);
            activeOrder = requireOwnedModifiableOrderForUpdate(cmd.orderId(), userId);
            
            eventManagementService.releaseSeats(activeOrder.getEventId(), activeOrder.getOrderId(), cmd.seatIds());
            removeSeatsFromActiveOrder(activeOrder, cmd.seatIds());

            AUDIT.info("op=removeSeatsFromExistingOrder order={} user={} event={} seatsRemoved={} result=ok",
                    cmd.orderId(), userId, activeOrder.getEventId(), cmd.seatIds().size());

        } catch (TimeExpiredException e) {
            expireAndSave(activeOrder);
            AUDIT.warn(
                    "op=removeSeatsFromExistingOrder order={} result=expired reason={}",
                    cmd != null ? cmd.orderId() : null,
                    e.getMessage());

            throw e;

        } catch (RuntimeException e) {
            AUDIT.warn("op=removeSeatsFromExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null, e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ActiveOrderView getActiveOrder(String token, UUID orderId) {
        try {
            UUID userId = requireValidUser(token);

            ActiveOrder activeOrder = requireActiveOrder(orderId);
            requireOrderOwnership(activeOrder, userId);
            activeOrder.ensureOrderIsModifiable();
            requireEventIsValid(activeOrder.getEventId());

            //TODO: ask for Set<tickets> that wiil simulatently check event's valisidity
            EventView eventView = eventManagementService.getEvent(activeOrder.getEventId());
            ActiveOrderView view = ActiveOrderView.from(activeOrder, eventView);

            AUDIT.info("op=getActiveOrder order={} user={} result=ok", orderId, userId);
            return view;

        } catch (RuntimeException e) {
            AUDIT.warn("op=getActiveOrder order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void checkout(String token, UUID orderId) {
        ActiveOrder activeOrder = null;
        boolean successfulPayment = false;
        boolean allTicketsIssued = false;

        try {
            UUID userId = requireValidUser(token);

            activeOrder = requireActiveOrderForUpdate(orderId);
            requireOrderOwnership(activeOrder, userId);
            ensureOrderIsModifiable(activeOrder);
            requireUserCanPurchaseInEvent(userId, activeOrder.getEventId());
            Set<Ticket> tickets = eventManagementService
            successfulPayment = pay(activeOrder);
            if (!successfulPayment) {
                throw new IllegalStateException("Payment failed");
            }
            allTicketsIssued = issueTickets(activeOrder);
            if (!allTicketsIssued) {
                throw new IllegalStateException("Failed to issue all tickets");
            }
            ConfirmationReceipt receipt = eventManagementService.confirm(activeOrder.getEventId(), activeOrder.getOrderId());
            finalizeSuccessfulCheckout(activeOrder);

            AUDIT.info("op=checkout order={} user={} event={} area={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    receipt.areaId(),
                    receipt.quantity());

        } catch (RuntimeException e) {
            if (successfulPayment) {
                paymentGateway.refund(activeOrder);
            }
            if (allTicketsIssued) {
                ticketProvider.revokeTickets(activeOrder);
            }
            AUDIT.warn("op=checkout order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void cancelAllActiveOrdersOfCurrentUser(String token) {
        try {
            UUID userId = requireValidUser(token);

            List<ActiveOrder> activeOrders =
                    activeOrderRepository.findByUserIdAndStatusForUpdate(
                            userId,
                            ActiveOrderStatus.ACTIVE
                    );

            for (ActiveOrder activeOrder : activeOrders) {
                cancelSingleActiveOrder(activeOrder);
            }

            AUDIT.info("op=cancelAllActiveOrdersOfCurrentUser user={} count={} result=ok",
                    userId, activeOrders.size());

        } catch (RuntimeException e) {
            AUDIT.warn("op=cancelAllActiveOrdersOfCurrentUser result=rejected reason={}",
                    e.getMessage());
            throw e;
        }
    }

    private UUID requireValidUser(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalStateException("Token is invalid or expired");
        }
        return auth.extractUserId(token);
    }

    private void requireOrderIsUniqueForUser(UUID userId, UUID eventId) {
        if (userId == null || eventId == null) {
            throw new IllegalArgumentException("User ID and event ID cannot be null");
        }
        if (activeOrderRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ActiveOrderStatus.ACTIVE)) {
            throw new IllegalStateException("User already has an active order for this event");
        }
    }

    private boolean issueTickets(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        
        boolean allIssued = ticketProvider.issueTickets(activeOrder);
        if (!allIssued) {
            AUDIT.warn("op=issueTickets order={} user={} event={} result=partial_failure",
                    activeOrder.getOrderId(),
                    activeOrder.getUserId(),
                    activeOrder.getEventId());
        }
        return allIssued;
    }

    private boolean pay(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        boolean paymentSuccessful = paymentGateway.charge(activeOrder);
        if (!paymentSuccessful) {
            AUDIT.warn("op=checkout order={} user={} event={} result=payment_failed",
                    activeOrder.getOrderId(),
                    activeOrder.getUserId(),
                    activeOrder.getEventId());
        }
        return paymentSuccessful;
    }

    private void addSeatsToActiveOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        if (activeOrder == null || seatIds == null) {
            throw new IllegalArgumentException("Active order and seat IDs cannot be null");
        }
        for (UUID seatId : seatIds) {
            activeOrder.addSeat(seatId);
        }
        activeOrderRepository.save(activeOrder);
    }

    private void removeSeatsFromActiveOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        if (activeOrder == null || seatIds == null) {
            throw new IllegalArgumentException("Active order and seat IDs cannot be null");
        }
        for (UUID seatId : seatIds) {
            activeOrder.removeSeat(seatId);
        }
        activeOrderRepository.save(activeOrder);
    }

    private void finalizeSuccessfulCheckout(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        activeOrder.complete();
        activeOrderRepository.save(activeOrder);

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(activeOrder);
        orderHistoryRepository.save(orderHistory);
    }

    private void cancelSingleActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        if (!activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId()
            );
        }

        activeOrder.cancel();
        activeOrderRepository.save(activeOrder);
    }

    private void expireAndSave(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        if (!activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId()
            );
        }

        activeOrder.expire();
        activeOrderRepository.save(activeOrder);
    }

    private void ensureOrderIsModifiable(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        if (activeOrder.getStatus() != ActiveOrderStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Order is not modifiable in its current status: " + activeOrder.getStatus());
        }

        if (activeOrder.hasTimeExpired()) {
            expireAndSave(activeOrder);
            throw new IllegalStateException("Order has expired");
        }

    }

    private void requireEventIsValid(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventManagementService.getEventAvailability(eventId) != EventAvailability.AVAILABLE) {
            throw new IllegalStateException("Event is not available for booking");
        }
    }

    private ActiveOrder requireActiveOrder(UUID orderId) {
        return activeOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found: " + orderId));
    }

    private ActiveOrder requireOwnedModifiableOrderForUpdate(UUID orderId, UUID userId) {
        ActiveOrder activeOrder = requireActiveOrderForUpdate(orderId);
        requireOrderOwnership(activeOrder, userId);
        ensureOrderIsModifiable(activeOrder);
        return activeOrder;
    }

    private ActiveOrder requireActiveOrderForUpdate(UUID orderId) {
        return activeOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found: " + orderId));
    }

    private void requireOrderOwnership(ActiveOrder activeOrder, UUID userId) {
        if (!activeOrder.getUserId().equals(userId)) {
            throw new IllegalStateException("User is not allowed to access this order");
        }
    }

    private void requireAddSeatsToActiveOrderCommand(AddSeatsToActiveOrderCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (cmd.orderId() == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (cmd.areaId() == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
        if (cmd.standingQuantity() != null && cmd.standingQuantity() < 0) {
            throw new IllegalArgumentException("Standing quantity cannot be negative");
        }
        requireSeatIds(cmd.seatIds());
    }

    private void requireRemoveSeatsFromActiveOrderCommand(RemoveSeatsFromActiveOrderCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (cmd.orderId() == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        requireSeatIds(cmd.seatIds());
    }

    private void requireSeatIds(Set<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs cannot be null or empty");
        }
        for (UUID seatId : seatIds) {
            if (seatId == null) {
                throw new IllegalArgumentException("Seat IDs cannot contain null values");
            }
        }
    }
}