package com.software_project_team_15b.Ticketmaster.white.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;

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
        Ticket ticket = new Ticket("TICKET-1", seatId1, price1);

        assertEquals("TICKET-1", ticket.getExternalTicketId());
        assertEquals(seatId1, ticket.getSeatId());

        Money returnedPrice = ticket.getBasePrice();
        assertEquals("100.00", returnedPrice.amount().toPlainString());
        assertEquals("ILS", returnedPrice.currency());
    }

    @Test
    void constructorShouldThrowWhenExternalTicketIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket(null, seatId1, price1));
    }

    @Test
    void constructorShouldThrowWhenExternalTicketIdIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("   ", seatId1, price1));
    }

    @Test
    void constructorShouldThrowWhenSeatIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("TICKET-2", null, price1));
    }

    @Test
    void constructorShouldThrowWhenPriceIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("TICKET-3", seatId1, null));
    }

    @Test
    void equalsShouldReturnTrueForSameInstance() {
        Ticket ticket = new Ticket("TICKET-4", seatId1, price1);

        assertEquals(ticket, ticket);
    }

    @Test
    void equalsShouldReturnTrueForTicketsWithSameExternalId() {
        Ticket ticket1 = new Ticket("TICKET-5", seatId1, price1);
        Ticket ticket2 = new Ticket("TICKET-5", seatId2, price2);

        assertEquals(ticket1, ticket2);
    }

    @Test
    void equalsShouldReturnFalseForTicketsWithDifferentExternalId() {
        Ticket ticket1 = new Ticket("TICKET-6", seatId1, price1);
        Ticket ticket2 = new Ticket("TICKET-7", seatId1, sameValueAsPrice1);

        assertNotEquals(ticket1, ticket2);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToNull() {
        Ticket ticket = new Ticket("TICKET-8", seatId1, price1);

        assertNotEquals(ticket, null);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToDifferentType() {
        Ticket ticket = new Ticket("TICKET-9", seatId1, price1);

        assertNotEquals(ticket, "not a ticket");
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Ticket ticket = new Ticket("TICKET-10", seatId1, price1);

        int first = ticket.hashCode();
        int second = ticket.hashCode();

        assertEquals(first, second);
    }

    @Test
    void hashCodeShouldBeEqualForTicketsWithSameExternalId() {
        Ticket ticket1 = new Ticket("TICKET-11", seatId1, price1);
        Ticket ticket2 = new Ticket("TICKET-11", seatId2, price2);

        assertEquals(ticket1.hashCode(), ticket2.hashCode());
    }

    @Test
    void hashCodeShouldBeDifferentForTicketsWithDifferentExternalId() {
        Ticket ticket1 = new Ticket("TICKET-12", seatId1, price1);
        Ticket ticket2 = new Ticket("TICKET-13", seatId1, price1);

        assertNotEquals(ticket1.hashCode(), ticket2.hashCode());
    }

    @Test
    void getBasePriceShouldReturnDefensiveCopy() {
        Ticket ticket = new Ticket("TICKET-14", seatId1, price1);

        Money returnedPrice = ticket.getBasePrice();

        assertNotSame(price1, returnedPrice);
        assertEquals(price1.amount(), returnedPrice.amount());
        assertEquals(price1.currency(), returnedPrice.currency());
    }

    @Test
    void constructorShouldStoreDefensiveCopyOfBasePrice() {
        Ticket ticket = new Ticket("TICKET-15", seatId1, price1);

        Money returnedPrice = ticket.getBasePrice();

        assertNotSame(price1, returnedPrice);
        assertEquals(price1.amount(), returnedPrice.amount());
        assertEquals(price1.currency(), returnedPrice.currency());
    }
}