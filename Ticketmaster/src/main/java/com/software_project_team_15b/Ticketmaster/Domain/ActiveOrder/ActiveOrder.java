package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

    ///NOTE: using String for orderId and eventId for now, will change to UUID if needed

    @Id 
    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    //using seat_id as a string for now, will change to a Seat entity if needed
    @ElementCollection
    @CollectionTable(name = "active_order_seats", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "seat_id", nullable = false)
    private Set<String> orderSeats = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActiveOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    private Long version;

    protected ActiveOrder() {
    }

    public ActiveOrder(String orderId, UUID userId, String eventId) {
        if (orderId == null || orderId.isBlank()) 
            throw new IllegalArgumentException("orderId cannot be null or empty");  
        if (userId == null || userId.toString().isBlank())
            throw new IllegalArgumentException("userId cannot be null or empty");
        if (eventId == null || eventId.isBlank()) 
            throw new IllegalArgumentException("eventId cannot be null or empty");
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.status = ActiveOrderStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = this.createdAt.plusMinutes(10);
    }

    public String getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEventId() {
        return eventId;
    }

    public Set<String> getOrderSeats() {
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

    //seat management methods
    private void validateSeatId(String seatId) {
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be null or empty");
        }
    }

    public void addSeat(String seatId) {
        ensureOrderIsModifiable();
        validateSeatId(seatId);
        if (!orderSeats.add(seatId)) {
            throw new IllegalArgumentException("Seat already in order");
        }
    }

    public void removeSeat(String seatId) {
        ensureOrderIsModifiable();
        validateSeatId(seatId);
        if (!orderSeats.remove(seatId)) {
            throw new IllegalArgumentException("Seat not found in order");
        }
        if (orderSeats.isEmpty()) {
            cancel();
        }
    }
    

    //order management methods
    public void complete() {
        ensureOrderIsModifiable();
        status = ActiveOrderStatus.COMPLETED;
    }

    public void cancel() {
        ensureOrderIsModifiable();
        status = ActiveOrderStatus.CANCELED;
    }
    
    //expiration management methods
    //NOTE: time-based expiration state changes are handled in the Service layer

    /*Checks expiration based on status*/
    public boolean isExpired() {    
        return status == ActiveOrderStatus.EXPIRED;
    }

    /*Checks expiration based on time*/
    public boolean hasTimeExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }


    // Only ACTIVE orders can transition to EXPIRED.
    // Time check is only relevant for ACTIVE orders.
    public void expire() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot expire");
        }
        if (!hasTimeExpired()) {
            throw new RuntimeException("Order " + orderId + " has not yet expired");
        }
        status = ActiveOrderStatus.EXPIRED;
    }

    public void ensureOrderIsModifiable() {
        if (status != ActiveOrderStatus.ACTIVE) {
            throw new UnactiveOrderException("Order " + orderId + " is not active and cannot be modified");
        }
        if (hasTimeExpired()) { 
            throw new TimeExpiredException("Order " + orderId + " has expired");
        }
    }
}


