package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

@Embeddable
public class Ticket {

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

    public Ticket(UUID seatId, Money basePrice) {
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }
        if (basePrice == null) {
            throw new IllegalArgumentException("Base price cannot be null");
        }

        this.seatId = seatId;
        this.basePrice = basePrice;
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
        if (o == null || getClass() != o.getClass()) return false;

        Ticket ticket = (Ticket) o;

        return seatId.equals(ticket.seatId) && basePrice.equals(ticket.basePrice);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(seatId, basePrice);
    }
}