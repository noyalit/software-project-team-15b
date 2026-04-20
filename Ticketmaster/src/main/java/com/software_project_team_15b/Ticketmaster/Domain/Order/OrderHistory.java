package com.software_project_team_15b.Ticketmaster.Domain.Order;

import jakarta.persistence.*;
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

    @OneToMany
    @JoinColumn(name = "order_id")
    private List<Ticket> tickets;

    protected OrderHistory() {
    }

    public OrderHistory(String orderId, String userId, String eventId, List<Ticket> tickets) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
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
        return tickets;
    }
}