package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Application.Auth;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Order.Commands.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PurchasingService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.purchasing");

    private final IActiveOrderRepository activeOrderRepository;
    private final IOrderHistoryRepository orderHistoryRepository;
    private final EventManagementService eventManagementService;
    private final Auth auth;

    public PurchasingService(IActiveOrderRepository activeOrderRepository,
                             IOrderHistoryRepository orderHistoryRepository,    
                             EventManagementService eventManagementService,
                             Auth auth) {
        if (activeOrderRepository == null || orderHistoryRepository == null || eventManagementService == null || auth == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.activeOrderRepository = activeOrderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
        this.eventManagementService = eventManagementService;
        this.auth = auth;
    }

    @Transactional
    public UUID createActiveOrder(String token, CreateActiveOrderCommand cmd) {

        try {
            requireValidToken(token);
            requireCreateActiveOrderCommand(cmd);

            UUID userId = extractUserId(token);
            UUID orderId = UUID.randomUUID();

            HoldCommand holdCommand = new HoldCommand(
                    cmd.areaId(),
                    cmd.seatIds(),
                    cmd.standingQuantity(),
                    orderId
            );

            HoldReceipt receipt = eventManagementService.hold(cmd.eventId(), holdCommand);

            ActiveOrder activeOrder = new ActiveOrder(orderId, userId, cmd.eventId());
            for (UUID seatId : cmd.seatIds()) {
                activeOrder.addSeat(seatId);
            }

            activeOrderRepository.save(activeOrder);

            AUDIT.info("op=createActiveOrder order={} user={} event={} area={} holdToken={} qty={} result=ok",
                    orderId, userId, cmd.eventId(), cmd.areaId(), holdToken, receipt.quantity());

            return orderId;

        } catch (RuntimeException e) {
            if (holdToken != null && cmd != null && cmd.eventId() != null) {
                eventManagementService.release(cmd.eventId(), holdToken);
            }
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason={}",
                    cmd != null ? cmd.eventId() : null, e.getMessage());
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
            activeOrder = requireActiveOrder(cmd.orderId());
            requireOrderOwnership(activeOrder, userId);

            HoldCommand holdCommand = new HoldCommand(
                    cmd.areaId(),
                    cmd.seatIds(),
                    cmd.standingQuantity(),
                    cmd.orderId()
            );

            HoldReceipt receipt = eventManagementService.hold(activeOrder.getEventId(), holdCommand);

            for (UUID seatId : cmd.seatIds()) {
                activeOrder.addSeat(seatId);
            }
            activeOrderRepository.save(activeOrder);

            AUDIT.info("op=addSeatsToExistingOrder order={} user={} event={} area={} holdToken={} qty={} result=ok",
                    cmd.orderId(), userId, activeOrder.getEventId(), cmd.areaId(), holdToken, receipt.quantity());

        } catch (Exception e) {
            if (holdToken != null && activeOrder != null) {
                eventManagementService.release(activeOrder.getEventId(), holdToken);
            }
            AUDIT.warn("op=addSeatsToExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void removeSeatsFromExistingOrder(String token, RemoveSeatsFromActiveOrderCommand cmd) {
        try {
            requireValidToken(token);
            requireRemoveSeatsFromActiveOrderCommand(cmd);

            UUID userId = extractUserId(token);
            ActiveOrder activeOrder = requireActiveOrder(cmd.orderId());
            requireOrderOwnership(activeOrder, userId);

            for (UUID seatId : cmd.seatIds()) {
                activeOrder.removeSeat(seatId);
            }
            activeOrderRepository.save(activeOrder);

            eventManagementService.release(activeOrder.getEventId(), activeOrder.getOrderId());

            AUDIT.info("op=removeSeatsFromExistingOrder order={} user={} event={} seatsRemoved={} result=ok",
                    cmd.orderId(), userId, activeOrder.getEventId(), cmd.seatIds().size());

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
            activeOrder.ensureOrderIsModifiable();

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

        try {
            requireValidToken(token);

            UUID userId = extractUserId(token);
            activeOrder = requireActiveOrder(orderId);
            requireOrderOwnership(activeOrder, userId);
            activeOrder.ensureOrderIsModifiable();

            ConfirmationReceipt receipt = confirmOrderSeatsPurchase(activeOrder);
            finalizeSuccessfulCheckout(activeOrder);

            AUDIT.info("op=checkout order={} user={} event={} area={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    receipt.areaId(),
                    receipt.quantity());

        } catch (RuntimeException e) {
            AUDIT.warn("op=checkout order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void cancelAllActiveOrdersOfCurrentUser(String token) {
        try {
            requireValidToken(token);

            UUID userId = extractUserId(token);

            List<ActiveOrder> activeOrders =
                    activeOrderRepository.findByUserIdAndActiveOrderStatus(userId, ActiveOrderStatus.ACTIVE);

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

    private ConfirmationReceipt confirmOrderSeatsPurchase(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        return eventManagementService.confirm(
                activeOrder.getEventId(),
                activeOrder.getOrderId()
        );
    }

    @Transactional
    private void finalizeSuccessfulCheckout(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        activeOrder.complete();
        activeOrderRepository.save(activeOrder);

        OrderHistory orderHistory = toOrderHistory(activeOrder);
        orderHistoryRepository.save(orderHistory);
    }

    @Transactional
    private void cancelSingleActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        activeOrder.cancel();

        activeOrderRepository.save(activeOrder);

        eventManagementService.release(
                activeOrder.getEventId(),
                activeOrder.getOrderId()
        );
    }

    @Transactional(readOnly = true)
    private ActiveOrder requireActiveOrder(UUID orderId) {
        return activeOrderRepository.findById(orderId)
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