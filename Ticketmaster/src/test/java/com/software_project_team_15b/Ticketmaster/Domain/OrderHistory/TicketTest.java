package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TicketTest {

    private UUID seatId1;
    private UUID seatId2;

    @BeforeEach
    void setUp() {
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();
    }

    @Test
    void constructorShouldSetSeatIdCorrectly() {
        Ticket ticket = new Ticket(seatId1);

        assertEquals(seatId1, ticket.getSeatId());
    }

    @Test
    void constructorShouldThrowWhenSeatIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Ticket(null));
    }

    @Test
    void equalsShouldReturnTrueForSameInstance() {
        Ticket ticket = new Ticket(seatId1);

        assertEquals(ticket, ticket);
    }

    @Test
    void equalsShouldReturnTrueForTicketsWithSameSeatId() {
        Ticket ticket1 = new Ticket(seatId1);
        Ticket ticket2 = new Ticket(seatId1);

        assertEquals(ticket1, ticket2);
        assertEquals(ticket1.hashCode(), ticket2.hashCode());
    }

    @Test
    void equalsShouldReturnFalseForTicketsWithDifferentSeatIds() {
        Ticket ticket1 = new Ticket(seatId1);
        Ticket ticket2 = new Ticket(seatId2);

        assertNotEquals(ticket1, ticket2);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToNull() {
        Ticket ticket = new Ticket(seatId1);

        assertNotEquals(ticket, null);
    }

    @Test
    void equalsShouldReturnFalseWhenComparedToDifferentType() {
        Ticket ticket = new Ticket(seatId1);

        assertNotEquals(ticket, "not a ticket");
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Ticket ticket = new Ticket(seatId1);

        int first = ticket.hashCode();
        int second = ticket.hashCode();

        assertEquals(first, second);
    }
}