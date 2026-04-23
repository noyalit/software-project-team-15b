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
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }    
        this.seatId = seatId;
    }

    public String getSeatId() {
        return seatId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ticket ticket = (Ticket) o;

        return seatId.equals(ticket.seatId);
    }

    @Override
    public int hashCode() {
        return seatId.hashCode();
    }

}