package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class OrderHistoryTest {

    private UUID orderId;
    private UUID userId;
    private UUID eventId;
    private UUID seatId1;
    private UUID seatId2;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();
    }

    @Test
    void fromActiveOrderShouldCopyFieldsAndSeats() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId);
        activeOrder.addSeat(seatId1);
        activeOrder.addSeat(seatId2);

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(activeOrder);

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());

        Set<UUID> ticketSeatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1, seatId2), ticketSeatIds);
    }

    @Test
    void constructorShouldInitializeFieldsCorrectly() {
        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                List.of(new Ticket(seatId1), new Ticket(seatId2))
        );

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());
        assertEquals(2, orderHistory.getTickets().size());
    }

    @Test
    void getTicketsShouldReturnDefensiveCopy() {
        Ticket ticket1 = new Ticket(seatId1);
        Ticket ticket2 = new Ticket(seatId2);

        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                List.of(ticket1, ticket2)
        );

        List<Ticket> returnedTickets = orderHistory.getTickets();
        returnedTickets.clear();

        List<Ticket> ticketsAfterMutationAttempt = orderHistory.getTickets();

        assertEquals(2, ticketsAfterMutationAttempt.size());
        assertTrue(ticketsAfterMutationAttempt.contains(ticket1));
        assertTrue(ticketsAfterMutationAttempt.contains(ticket2));
    }

    @Test
    void constructorShouldCopyTicketsList() {
        List<Ticket> originalTickets = new ArrayList<>();
        originalTickets.add(new Ticket(seatId1));

        OrderHistory orderHistory = new OrderHistory(orderId, userId, eventId, originalTickets);

        originalTickets.clear();

        assertEquals(1, orderHistory.getTickets().size());
        assertEquals(seatId1, orderHistory.getTickets().get(0).getSeatId());
    }

    @Test
    void constructorShouldThrowWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(null, userId, eventId, List.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, null, eventId, List.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, null, List.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsListIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, null)
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsContainNull() {
        List<Ticket> tickets = new ArrayList<>();
        tickets.add(new Ticket(seatId1));
        tickets.add(null);

        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, tickets)
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenActiveOrderIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(null)
        );
    }
}