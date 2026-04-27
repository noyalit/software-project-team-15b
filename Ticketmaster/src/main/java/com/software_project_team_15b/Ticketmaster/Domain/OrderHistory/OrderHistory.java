package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private List<Ticket> tickets = new ArrayList<>();

    protected OrderHistory() {
    }

    public OrderHistory(UUID orderId, UUID userId, UUID eventId, List<Ticket> tickets) {
        if (orderId == null || userId == null || eventId == null || tickets == null) {
            throw new IllegalArgumentException("Order ID, User ID, Event ID, and Tickets cannot be null");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        for (Ticket ticket : tickets) {
            if (ticket == null) {
                throw new IllegalArgumentException("Tickets list cannot contain null values");
            }
            this.tickets.add(new Ticket(ticket.getSeatId()));
        }
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
                        .toList()
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

    public List<Ticket> getTickets() {
        return new ArrayList<>(tickets);
    }
}