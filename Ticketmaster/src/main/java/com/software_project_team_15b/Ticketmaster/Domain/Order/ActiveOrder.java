package com.software_project_team_15b.Ticketmaster.Domain.Order;

import java.util.List;

public class ActiveOrder {

    private final String orderId;
    private final String userId;
    private final String eventId;
    private final List<String> ticketSeats;

    public ActiveOrder(String orderId, String userId, String eventId, List<String> ticketSeats) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.ticketSeats = ticketSeats;
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

    public List<String> getTicketSeats() {
        return ticketSeats;
    }
}
