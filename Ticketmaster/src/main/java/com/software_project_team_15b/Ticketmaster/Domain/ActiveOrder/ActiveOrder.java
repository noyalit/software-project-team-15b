package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.UnactiveOrderException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

// Orders are intentionally not reactivated after leaving ACTIVE status.
@Entity
@Table(
        name = "active_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_active_order_user_event_active",
                        columnNames = {"user_id", "event_id", "active_uniqueness_key"}
                )
        }
)
public class ActiveOrder {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID areaId;

    @ElementCollection
    @CollectionTable(
            name = "active_order_seats",
            joinColumns = @JoinColumn(name = "order_id")
    )
    @Column(name = "seat_id", nullable = false)
    private Set<UUID> orderSeats = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActiveOrderStatus status;


    /**
     * Technical field used only for the database uniqueness constraint.
     *
     * ACTIVE orders have activeUniquenessKey = true.
     * Non-active orders have activeUniquenessKey = null.
     *
     * This allows the database to enforce only one active order per user/event,
     * while allowing multiple completed/canceled/expired historical orders
     * for the same user/event because unique constraints allow multiple NULLs.
     */
    @Column(name = "activeUniquenessKey", nullable = true)
    private Boolean activeUniquenessKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // null means: regular ACTIVE order, not in checkout yet.
    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Version
    private Long version;

    protected ActiveOrder() {
    }

    public ActiveOrder(UUID orderId, UUID userId, UUID eventId, UUID areaId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (areaId == null) {
            throw new IllegalArgumentException("areaId cannot be null");
        }

        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.areaId = areaId;
        this.status = ActiveOrderStatus.ACTIVE;
        this.activeUniquenessKey = true;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = null;
    }
    // Constructor for testing purposes
    public ActiveOrder(UUID orderId,
                   UUID userId,
                   UUID eventId,
                   LocalDateTime createdAt,
                   LocalDateTime expiresAt) {

        if (orderId == null || userId == null || eventId == null) {
            throw new IllegalArgumentException("IDs cannot be null");
        }

        if (createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Dates cannot be null");
        }

        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before createdAt");
        }

        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;

        this.createdAt = createdAt;
        this.expiresAt = expiresAt;

        this.status = ActiveOrderStatus.ACTIVE;
        this.activeUniquenessKey = true;
        this.orderSeats = new HashSet<>();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public Set<UUID> getOrderSeats() {
        return Set.copyOf(orderSeats);
    }

    public ActiveOrderStatus getStatus() {
        return status;
    }

    // isActive is used for the unique constraint to allow multiple non-active orders for the same user and event
    public Boolean getActiveUniquenessKey() {
        return activeUniquenessKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Long getVersion() {
        return version;
    }

    public void addSeats(Set<UUID> seatIds) {
        ensureOrderIsModifiable();
        validateSeatIds(seatIds);

        Set<UUID> duplicates = seatIds.stream()
                .filter(orderSeats::contains)
                .collect(java.util.stream.Collectors.toSet());

        if (!duplicates.isEmpty()) {
            throw new AlreadyDoneException("Some selected seats are already taken by another order.");
        }

        orderSeats.addAll(seatIds);
    }

    public void removeSeats(Set<UUID> seatIds) {
        ensureOrderIsModifiable();
        validateSeatIds(seatIds);

        Set<UUID> missingSeats = seatIds.stream()
                .filter(seatId -> !orderSeats.contains(seatId))
                .collect(java.util.stream.Collectors.toSet());

        if (!missingSeats.isEmpty()) {
            throw new AlreadyDoneException("Some selected seats are not in your order.");
        }

        orderSeats.removeAll(seatIds);
    }

    public void startCheckout(LocalDateTime checkoutExpiresAt) {
        ensureOrderIsModifiable();

        if (orderSeats.isEmpty()) {
            throw new IllegalStateException("Cannot start checkout for an empty order");
        }
        if (checkoutExpiresAt == null) {
            throw new IllegalArgumentException("checkoutExpiresAt cannot be null");
        }
        if (!checkoutExpiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("checkoutExpiresAt must be in the future");
        }

        this.expiresAt = checkoutExpiresAt;
    }

    public void complete() {
        ensureOrderIsInCheckout();
        changeStatusFromActive(ActiveOrderStatus.COMPLETED);
    }

    public void cancel() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot be canceled");
        }

        changeStatusFromActive(ActiveOrderStatus.CANCELED);
    }

    public boolean isExpired() {
        return status == ActiveOrderStatus.EXPIRED;
    }

    public boolean isInCheckout() {
        return status == ActiveOrderStatus.ACTIVE
                && expiresAt != null
                && !hasTimeExpired();
    }

    public boolean hasTimeExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void expire() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot expire");
        }
        if (expiresAt == null) {
            throw new IllegalStateException("Order " + orderId + " is not in checkout and cannot expire");
        }
        if (!hasTimeExpired()) {
            throw new IllegalStateException("Order " + orderId + " checkout has not expired yet");
        }

        changeStatusFromActive(ActiveOrderStatus.EXPIRED);
    }

    public void ensureOrderIsActive() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active");
        }
        if (hasTimeExpired()) {
            throw new TimeExpiredException("Order " + orderId + " checkout has expired");
        }
    }

    public void ensureOrderIsModifiable() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot be modified");
        }

        if (expiresAt != null) {
            if (hasTimeExpired()) {
                throw new TimeExpiredException("Order " + orderId + " checkout has expired");
            }
            throw new IllegalStateException("Order " + orderId + " is already in checkout");
        }
    }

    public void ensureOrderIsInCheckout() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active");
        }

        if (expiresAt == null) {
            throw new IllegalStateException("Order " + orderId + " is not in checkout");
        }

        if (hasTimeExpired()) {
            throw new TimeExpiredException("Order " + orderId + " checkout has expired");
        }
    }

    private void validateSeatIds(Set<UUID> seatIds) {
        if (seatIds == null) {
            throw new IllegalArgumentException("seatIds cannot be null");
        }
        for (UUID seatId : seatIds) {
             if (seatId == null) {
                throw new IllegalArgumentException("seatId cannot be null");
            }
        }
    }

    private void changeStatusFromActive(ActiveOrderStatus newStatus) {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot change status to " + newStatus);
        }
        if (newStatus == ActiveOrderStatus.ACTIVE) {
            throw new IllegalArgumentException("New status must be different from ACTIVE");
        }

        this.status = newStatus;
        syncActiveUniquenessKey();
    }

    private void syncActiveUniquenessKey() {
        this.activeUniquenessKey =
            status == ActiveOrderStatus.ACTIVE ? Boolean.TRUE : null;
    }
}