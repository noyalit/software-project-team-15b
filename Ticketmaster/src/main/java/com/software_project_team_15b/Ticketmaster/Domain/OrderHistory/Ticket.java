package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class Ticket {

    @Column(name = "seat_id", nullable = false, updatable = false)
    private UUID seatId;

    protected Ticket() {
    }

    public Ticket(UUID seatId) {
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }    
        this.seatId = seatId;
    }

    public UUID getSeatId() {
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