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
                    column = @Column(name = "price_amount", nullable = false, updatable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "price_currency", nullable = false, updatable = false)
            )
    })
    private Money price;

    protected Ticket() {
    }

    public Ticket(UUID seatId, Money price) {
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }

        this.seatId = seatId;
        this.price = price;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public Money getPrice() {
        return new Money(price.amount(), price.currency());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ticket ticket = (Ticket) o;

        return seatId.equals(ticket.seatId) && price.equals(ticket.price);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(seatId, price);
    }
}