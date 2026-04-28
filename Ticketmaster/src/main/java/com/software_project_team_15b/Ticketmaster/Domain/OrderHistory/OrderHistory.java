package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import jakarta.persistence.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

@Entity
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ElementCollection
    @CollectionTable(
        name = "order_history_tickets",
        joinColumns = @JoinColumn(name = "order_id")
    )
    private Set<Ticket> tickets = new java.util.HashSet<>();

    protected OrderHistory() {
    }

    public OrderHistory(UUID orderId, UUID userId, UUID eventId, Set<Ticket> tickets) {
        if (orderId == null || userId == null || eventId == null || tickets == null || tickets.isEmpty()) {
            throw new IllegalArgumentException("Order ID, User ID, Event ID, and Tickets cannot be null or empty");
        }
        if (tickets.contains(null)) {
            throw new IllegalArgumentException("Tickets set cannot contain null values");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = new java.util.HashSet<>(tickets);
    }

    public static OrderHistory fromActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        return new OrderHistory(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getOrderSeats().stream()
                        .map(seat -> new Ticket(seat))
                        .collect(Collectors.toSet())
        );
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

    public Set<Ticket> getTickets() {
        return Set.copyOf(tickets);
    }
}