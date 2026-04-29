package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TicketTest {

    private UUID seatId1;
    private UUID seatId2;
    private Money price1;
    private Money price2;
    private Money sameValueAsPrice1;

    @BeforeEach
    void setUp() {
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        price1 = Money.of("100.00", "ILS");
        price2 = Money.of("150.00", "ILS");
        sameValueAsPrice1 = Money.of("100.00", "ILS");
    }

    @Test
    void constructorShouldSetFieldsCorrectly() {
        Ticket ticket = new Ticket(seatId1, price1);

        assertEquals(seatId1, ticket.getSeatId());

        Money returnedPrice = ticket.getPrice();
        assertEquals("100.00", returnedPrice.amount().toPlainString());
        assertEquals("ILS", returnedPrice.currency());
    }

    @Test
    void constructorShouldThrowWhenSeatIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Ticket(null, price1));
    }

    @Test
    void constructorShouldThrowWhenPriceIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Ticket(seatId1, null));
    }

    @Test
    void equalsShouldReturnTrueForSameInstance() {
        Ticket ticket = new Ticket(seatId1, price1);

        assertEquals(ticket, ticket);
    }

    @Test
    void equalsShouldReturnTrueForTicketsWithSameSeatIdAndSamePriceValue() {
        Ticket ticket1 = new Ticket(seatId1, price1);
        Ticket ticket2 = new Ticket(seatId1, sameValueAsPrice1);

        assertEquals(ticket1, ticket2);
        assertEquals(ticket1.hashCode(), ticket2.hashCode());
    }

    @Test
    void equalsShouldReturnFalseForTicketsWithSameSeatIdButDifferentPrice() {
        Ticket ticket1 = new Ticket(seatId1, price1);
        Ticket ticket2 = new Ticket(seatId1, price2);

        assertNotEquals(ticket1, ticket2);
    }

    @Test
    void equalsShouldReturnFalseForTicketsWithDifferentSeatIds() {
        Ticket ticket1 = new Ticket(seatId1, price1);
        Ticket ticket2 = new Ticket(seatId2, price1);

        assertNotEquals(ticket1, ticket2);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToNull() {
        Ticket ticket = new Ticket(seatId1, price1);

        assertNotEquals(ticket, null);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToDifferentType() {
        Ticket ticket = new Ticket(seatId1, price1);

        assertNotEquals(ticket, "not a ticket");
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Ticket ticket = new Ticket(seatId1, price1);

        int first = ticket.hashCode();
        int second = ticket.hashCode();

        assertEquals(first, second);
    }

    @Test
    void getPriceShouldReturnDefensiveCopy() {
        Ticket ticket = new Ticket(seatId1, price1);

        Money returnedPrice = ticket.getPrice();

        assertNotSame(price1, returnedPrice);
        assertEquals(price1.amount(), returnedPrice.amount());
        assertEquals(price1.currency(), returnedPrice.currency());
    }
}