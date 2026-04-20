package com.software_project_team_15b.Ticketmaster.Domain.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Ticket {

    @Column(name = "event_area_id", nullable = false, updatable = false)
    private String eventAreaId;

    @Column(name = "seat_id", nullable = false, updatable = false)
    private String seatId;

    protected Ticket() {
    }

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