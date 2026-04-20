package com.software_project_team_15b.Ticketmaster.Domain.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Ticket {

    @Column(name = "seat_id", nullable = false, updatable = false)
    private String seatId;

    protected Ticket() {
    }

    public Ticket(String seatId) {
        this.seatId = seatId;
    }

    public String getSeatId() {
        return seatId;
    }
}