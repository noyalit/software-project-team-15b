package com.software_project_team_15b.Ticketmaster.Domain.Order;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @ElementCollection
    @CollectionTable(
        name = "order_history_tickets",
        joinColumns = @JoinColumn(name = "order_id")
    )
    private List<Ticket> tickets;

    protected OrderHistory() {
    }

    public OrderHistory(String orderId, String userId, String eventId, List<Ticket> tickets) {
        if (orderId == null || userId == null || eventId == null || tickets == null) {
            throw new IllegalArgumentException("Order ID, User ID, Event ID, and Tickets cannot be null");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = new ArrayList<>(tickets);
    }

    public static OrderHistory fromActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        return new OrderHistory(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getTicketSeats().stream()
                        .map(seat -> new Ticket(seat))
                        .toList()
        );
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEventId() {
        return eventId;
    }

    public List<Ticket> getTickets() {
        return new ArrayList<>(tickets);
    }
}