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
import jakarta.persistence.Version;

@Entity
@Table(name = "active_orders")
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
        this.createdAt = LocalDateTime.now();
        this.expiresAt = null;
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
            throw new AlreadyDoneException("Seats already in order: " + duplicates);
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
            throw new AlreadyDoneException("Seats not found in order: " + missingSeats);
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

        status = ActiveOrderStatus.COMPLETED;
    }

    public void cancel() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot be canceled");
        }

        status = ActiveOrderStatus.CANCELED;
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

        status = ActiveOrderStatus.EXPIRED;
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
}