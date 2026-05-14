package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class PurchasingDomainService {

    private final IActiveOrderRepository activeOrderRepository;
    private final IOrderHistoryRepository orderHistoryRepository;

    public PurchasingDomainService(
            IActiveOrderRepository activeOrderRepository,
            IOrderHistoryRepository orderHistoryRepository
    ) {
        this.activeOrderRepository = Objects.requireNonNull(activeOrderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
    }

    public UUID createActiveOrder(UUID userId, UUID eventId, UUID areaId) {

        UUID orderId = UUID.randomUUID();
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrderRepository.saveAndFlush(activeOrder);
        return orderId;
    }

    public void requireEventCanBeBooked(UUID eventId, EventAvailability eventAvailability) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventAvailability != EventAvailability.AVAILABLE) {
            throw new IllegalStateException("Event is not available for booking");
        }
    }

    public void requireAreaCanBeBooked(UUID areaId, boolean areaAvailable) {
        if (areaId == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
        if (!areaAvailable) {
            throw new IllegalStateException("Area is not available for booking");
        }
    }

    public void requirePurchaseAccess(
            UUID userId,
            UUID eventId,
            LotteryEligibilityDTO lotteryEligibilityDTO,
            boolean queueAccess
    ) {
        if (userId == null || eventId == null || lotteryEligibilityDTO == null) {
            throw new IllegalArgumentException("User ID, event ID, and lottery eligibility result cannot be null");
        }
        if (!lotteryEligibilityDTO.canCreateActiveOrder()) {
            throw new IllegalStateException("User is not eligible to create an active order for this event: " + lotteryEligibilityDTO.status());
        }
        if (!queueAccess) {
            throw new TimeExpiredException("User does not have access to create or modify orders for this event at the moment");
        }
    }

    public ActiveOrder getOwnedOrderForUpdate(UUID userId, UUID orderId) {
        ActiveOrder activeOrder = requireActiveOrderForUpdate(orderId);
        requireOrderOwnership(activeOrder, userId);
        return activeOrder;
    }

    public ActiveOrder getOwnedActiveOrderForUpdate(UUID userId, UUID orderId) {
        ActiveOrder activeOrder = getOwnedOrderForUpdate(userId, orderId);
        validateOrderIsActive(activeOrder);
        return activeOrder;
    }

    public ActiveOrder getOwnedModifiableOrderForUpdate(UUID userId, UUID orderId) {
        ActiveOrder activeOrder = getOwnedOrderForUpdate(userId, orderId);
        validateOrderIsModifiable(activeOrder);
        return activeOrder;
    }

    public ActiveOrder getOwnedCheckoutOrderForUpdate(UUID userId, UUID orderId) {
        ActiveOrder activeOrder = getOwnedOrderForUpdate(userId, orderId);
        validateOrderIsInCheckout(activeOrder);
        return activeOrder;
    }

    public void validateOrderIsActive(ActiveOrder activeOrder) {
        requireOrderIsActive(activeOrder);
    }

    public void validateOrderIsModifiable(ActiveOrder activeOrder) {
        ensureOrderIsModifiable(activeOrder);
    }

    public void validateOrderIsInCheckout(ActiveOrder activeOrder) {
        ensureOrderIsInCheckout(activeOrder);
    }

    public void addSeatsToOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        requireActiveOrder(activeOrder);
        requireSeatIds(seatIds);
        activeOrder.addSeats(seatIds);
        activeOrderRepository.save(activeOrder);
    }

    public void removeSeatsFromOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        requireActiveOrder(activeOrder);
        requireSeatIds(seatIds);
        activeOrder.removeSeats(seatIds);
        activeOrderRepository.save(activeOrder);
    }

    public Set<UUID> requireRequestedSeatsAvailable(Map<Boolean, Set<UUID>> seatsAvailability, Set<UUID> requestedSeatIds) {
        requireSeatIds(requestedSeatIds);
        if (seatsAvailability == null) {
            throw new IllegalArgumentException("Seat availability cannot be null");
        }

        Set<UUID> unavailableSeats = seatsAvailability.getOrDefault(false, Set.of());
        Set<UUID> availableSeats = seatsAvailability.getOrDefault(true, Set.of());

        if (!unavailableSeats.isEmpty()) {
            throw new IllegalStateException("Some seats are not available: " + unavailableSeats);
        }

        if (availableSeats.size() != requestedSeatIds.size()) {
            throw new IllegalStateException("Seat availability response is inconsistent");
        }

        return availableSeats;
    }

    public boolean removeUnavailableSeatsFromOrder(ActiveOrder activeOrder, Set<UUID> unavailableSeats) {
        requireActiveOrder(activeOrder);
        if (unavailableSeats == null) {
            throw new IllegalArgumentException("Unavailable seats cannot be null");
        }
        if (unavailableSeats.isEmpty()) {
            return false;
        }
        removeSeatsFromOrder(activeOrder, unavailableSeats);
        return true;
    }

    public boolean syncOrderSeatsAvailability(ActiveOrder activeOrder, Map<Boolean, Set<UUID>> seatsAvailability) {
        requireActiveOrder(activeOrder);
        if (activeOrder.getOrderSeats().isEmpty()) {
            return false;
        }
        if (seatsAvailability == null) {
            throw new IllegalArgumentException("Seat availability cannot be null");
        }
        Set<UUID> unavailableSeats = seatsAvailability.getOrDefault(false, Set.of());
        return removeUnavailableSeatsFromOrder(activeOrder, unavailableSeats);
    }

    public PurchaseRequest buildPurchaseRequest(ActiveOrder activeOrder, LocalDate birthDate) {
        requireActiveOrder(activeOrder);
        return new PurchaseRequest(
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                activeOrder.getUserId(),
                birthDate,
                activeOrder.getOrderSeats().size(),
                new ArrayList<>(activeOrder.getOrderSeats()),
                null
        );
    }

    public LocalDateTime startCheckout(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        activeOrder.startCheckout(expiresAt);
        activeOrderRepository.save(activeOrder);
        return expiresAt;
    }

    public ActiveOrder finalizeCheckout(ActiveOrder activeOrder, PriceBreakdown pricing) {
        requireActiveOrder(activeOrder);
        if (pricing == null) {
            throw new IllegalArgumentException("Pricing cannot be null");
        }

        activeOrder.complete();
        activeOrderRepository.save(activeOrder);

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(activeOrder, pricing.total(), pricing.basePrice());
        orderHistoryRepository.save(orderHistory);
        return activeOrder;
    }

    public List<ActiveOrder> getActiveOrdersOfUserForUpdate(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return activeOrderRepository.findByUserIdAndStatusForUpdate(userId, ActiveOrderStatus.ACTIVE);
    }

    public void cancelOrder(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        activeOrder.cancel();
        activeOrderRepository.save(activeOrder);
    }

    public void expireOrder(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        activeOrder.expire();
        activeOrderRepository.save(activeOrder);
    }

    public boolean shouldReleaseSeatsForExpiredActiveOrder(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        return activeOrder.getStatus() == ActiveOrderStatus.ACTIVE
                && activeOrder.hasTimeExpired()
                && !activeOrder.isExpired()
                && !activeOrder.getOrderSeats().isEmpty();
    }

    public boolean shouldReleaseHoldBeforeCancel(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        return activeOrder.getStatus() == ActiveOrderStatus.ACTIVE
                && activeOrder.getExpiresAt() != null
                && !activeOrder.getOrderSeats().isEmpty();
    }

    private ActiveOrder requireActiveOrderForUpdate(UUID orderId) {
        return activeOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found: " + orderId));
    }

    private void requireOrderOwnership(ActiveOrder activeOrder, UUID userId) {
        requireActiveOrder(activeOrder);
        if (!activeOrder.getUserId().equals(userId)) {
            throw new IllegalStateException("User is not allowed to access this order");
        }
    }

    private void requireOrderIsActive(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        activeOrder.ensureOrderIsActive();
    }

    private void ensureOrderIsModifiable(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        activeOrder.ensureOrderIsModifiable();
    }

    private void ensureOrderIsInCheckout(ActiveOrder activeOrder) {
        requireActiveOrder(activeOrder);
        activeOrder.ensureOrderIsInCheckout();
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

    private void requireActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
    }
}
