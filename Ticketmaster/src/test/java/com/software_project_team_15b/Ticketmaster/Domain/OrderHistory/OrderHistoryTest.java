package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
                Set.of(new Ticket(seatId1), new Ticket(seatId2))
        );

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());

        Set<UUID> ticketSeatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1, seatId2), ticketSeatIds);
    }

    @Test
    void getTicketsShouldProtectInternalState() {
        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                Set.of(new Ticket(seatId1), new Ticket(seatId2))
        );

        Set<Ticket> returnedTickets = orderHistory.getTickets();

        assertThrows(UnsupportedOperationException.class, () ->
                returnedTickets.add(new Ticket(UUID.randomUUID()))
        );

        Set<UUID> ticketSeatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1, seatId2), ticketSeatIds);
    }

    @Test
    void constructorShouldCopyTicketsSet() {
        Set<Ticket> originalTickets = new HashSet<>();
        originalTickets.add(new Ticket(seatId1));

        OrderHistory orderHistory = new OrderHistory(orderId, userId, eventId, originalTickets);

        originalTickets.clear();

        Set<UUID> seatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1), seatIds);
    }

    @Test
    void constructorShouldThrowWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(null, userId, eventId, Set.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, null, eventId, Set.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, null, Set.of(new Ticket(seatId1)))
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsSetIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, null)
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsSetIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, Set.of())
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsContainNull() {
        Set<Ticket> tickets = new HashSet<>();
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

    @Test
    void constructorShouldRemoveDuplicateTicketsBySeatId() {
        Set<Ticket> tickets = new HashSet<>();
        tickets.add(new Ticket(seatId1));
        tickets.add(new Ticket(seatId1));

        OrderHistory orderHistory = new OrderHistory(orderId, userId, eventId, tickets);

        Set<UUID> seatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(1, seatIds.size());
        assertEquals(Set.of(seatId1), seatIds);
    }
}