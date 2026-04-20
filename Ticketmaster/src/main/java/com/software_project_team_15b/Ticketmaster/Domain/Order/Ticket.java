package com.software_project_team_15b.Ticketmaster.Domain.Order;

public class Ticket {
    private final String eventAreaId;
    private final String seatId;

    public Ticket(String eventAreaId, String seatId) {
        this.eventAreaId = eventAreaId;
        this.seatId = seatId;
    }

    public String getEventAreaId() {
        return eventAreaId;
    }

    public String getSeatId() {
        return seatId;
    }

}
