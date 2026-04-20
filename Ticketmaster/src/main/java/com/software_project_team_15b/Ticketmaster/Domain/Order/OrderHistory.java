package com.software_project_team_15b.Ticketmaster.Domain.Order;

import java.util.List;

public class OrderHistory {
    private final String orderId;
    private final String userId;
    private final String eventId;
    private final List<Ticket> tickets;

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
