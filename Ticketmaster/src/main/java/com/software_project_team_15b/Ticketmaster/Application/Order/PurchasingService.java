package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Application.Auth;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Order.Commands.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PurchasingService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.purchasing");

    private final IActiveOrderRepository activeOrderRepository;
    private final IOrderHistoryRepository orderHistoryRepository;
    private final EventManagementService eventManagementService;
    private final PaymentGateway paymentGateway;
    private final TicketProvider ticketProvider;
    private final Auth auth;

    public PurchasingService(IActiveOrderRepository activeOrderRepository,
            IOrderHistoryRepository orderHistoryRepository,
            EventManagementService eventManagementService,
            PaymentGateway paymentGateway,
            TicketProvider ticketProvider,
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
            requireValidToken(token);
            requireEventIsValid(eventId);

            UUID userId = extractUserId(token);
            requireOrderIsUniqueForUser(userId, eventId);

            UUID orderId = UUID.randomUUID();

            ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId);

            activeOrderRepository.save(activeOrder);

            AUDIT.info("op=createActiveOrder order={} user={} event={} result=ok",
                    orderId, userId, eventId);

            return orderId;

        } catch (RuntimeException e) {
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
            requireValidToken(token);
            requireAddSeatsToActiveOrderCommand(cmd);

            UUID userId = extractUserId(token);

            activeOrder = requireActiveOrderForUpdate(cmd.orderId());
            requireOrderOwnership(activeOrder, userId);

            ensureOrderIsModifiable(activeOrder);

            HoldCommand holdCommand = new HoldCommand(
                    cmd.areaId(),
                    cmd.seatIds(),
                    cmd.standingQuantity(),
                    activeOrder.getOrderId());

            HoldReceipt receipt = eventManagementService.hold(activeOrder.getEventId(), holdCommand);

            for (UUID seatId : cmd.seatIds()) {
                activeOrder.addSeat(seatId);
            }

            activeOrderRepository.save(activeOrder);

            AUDIT.info(
                    "op=addSeatsToExistingOrder order={} user={} event={} area={} holdToken={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    cmd.areaId(),
                    activeOrder.getOrderId(),
                    receipt.quantity());

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
            requireValidToken(token);
            requireRemoveSeatsFromActiveOrderCommand(cmd);

            UUID userId = extractUserId(token);
            activeOrder = requireActiveOrderForUpdate(cmd.orderId());
            requireOrderOwnership(activeOrder, userId);

            ensureOrderIsModifiable(activeOrder);

            for (UUID seatId : cmd.seatIds()) {
                activeOrder.removeSeat(seatId);
            }
            activeOrderRepository.save(activeOrder);

            eventManagementService.releaseSeats(activeOrder.getEventId(), activeOrder.getOrderId(), cmd.seatIds());

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
            requireValidToken(token);

            UUID userId = extractUserId(token);
            ActiveOrder activeOrder = requireActiveOrder(orderId);
            requireOrderOwnership(activeOrder, userId);
            ensureOrderIsModifiable(activeOrder);
            requireEventIsValid(activeOrder.getEventId());

            EventView eventView = eventManagementService.getEvent(activeOrder.getEventId());
            ActiveOrderView view = toActiveOrderView(activeOrder, eventView);

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
            requireValidToken(token);

            UUID userId = extractUserId(token);
            activeOrder = requireActiveOrderForUpdate(orderId);
            requireOrderOwnership(activeOrder, userId);
            ensureOrderIsModifiable(activeOrder);
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
            requireValidToken(token);

            UUID userId = extractUserId(token);

            List<ActiveOrder> activeOrders = activeOrderRepository.findByUserIdAndStatus(userId,
                    ActiveOrderStatus.ACTIVE);

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

    private void requireValidToken(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalStateException("Token is invalid or expired");
        }
    }

    private UUID extractUserId(String token) {
        try {
            return auth.extractUserId(token);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Token does not contain a valid user ID", e);
        }
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

        activeOrder.cancel();

        activeOrderRepository.save(activeOrder);

        if (!activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId());
        }
    }

    private void expireAndSave(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        activeOrder.expire();
        activeOrderRepository.save(activeOrder);

        if (!activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(activeOrder.getEventId(), activeOrder.getOrderId());
        }
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

    private ActiveOrder requireActiveOrderForUpdate(UUID orderId) {
        return activeOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found: " + orderId));
    }

    private void requireOrderOwnership(ActiveOrder activeOrder, UUID userId) {
        if (!activeOrder.getUserId().equals(userId)) {
            throw new IllegalStateException("User is not allowed to access this order");
        }
    }

    private void requireCreateActiveOrderCommand(CreateActiveOrderCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (cmd.eventId() == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (cmd.areaId() == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
        requireSeatIds(cmd.seatIds());
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