package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.util.Objects;
import java.util.UUID;

@Embeddable
public class Ticket {

    @Column(name = "external_ticket_id", nullable = false, updatable = false)
    private String externalTicketId;

    @Column(name = "seat_id", nullable = false, updatable = false)
    private UUID seatId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "base_price_amount", nullable = false, updatable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "base_price_currency", nullable = false, updatable = false)
            )
    })
    private Money basePrice;

    protected Ticket() {
    }

    public Ticket(String externalTicketId, UUID seatId, Money basePrice) {
        if (externalTicketId == null || externalTicketId.isBlank()) {
            throw new IllegalArgumentException("External ticket ID cannot be null or blank");
        }
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }
        if (basePrice == null) {
            throw new IllegalArgumentException("Base price cannot be null");
        }

        this.externalTicketId = externalTicketId;
        this.seatId = seatId;
        this.basePrice = new Money(basePrice.amount(), basePrice.currency());
    }

    public String getExternalTicketId() {
        return externalTicketId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public Money getBasePrice() {
        return new Money(basePrice.amount(), basePrice.currency());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ticket ticket)) return false;
        return externalTicketId.equals(ticket.externalTicketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalTicketId);
    }
}