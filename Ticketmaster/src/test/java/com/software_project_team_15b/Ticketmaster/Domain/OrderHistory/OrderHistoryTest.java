package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

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

    private Money price1;
    private Money price2;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        price1 = Money.of("100.00", "ILS");
        price2 = Money.of("150.00", "ILS");
    }

    @Test
    void fromActiveOrderShouldCopyFieldsAndTickets() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId);
        activeOrder.addSeat(seatId1);
        activeOrder.addSeat(seatId2);

        Set<Ticket> tickets = Set.of(
                new Ticket(seatId1, price1),
                new Ticket(seatId2, price2)
        );

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(activeOrder, tickets);

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());

        assertEquals(tickets, orderHistory.getTickets());
    }

    @Test
    void constructorShouldInitializeFieldsCorrectly() {
        Set<Ticket> tickets = Set.of(
                new Ticket(seatId1, price1),
                new Ticket(seatId2, price2)
        );

        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                tickets
        );

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());
        assertEquals(tickets, orderHistory.getTickets());
    }

    @Test
    void getTicketsShouldProtectInternalState() {
        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                Set.of(
                        new Ticket(seatId1, price1),
                        new Ticket(seatId2, price2)
                )
        );

        Set<Ticket> returnedTickets = orderHistory.getTickets();

        assertThrows(UnsupportedOperationException.class, () ->
                returnedTickets.add(new Ticket(UUID.randomUUID(), price1))
        );

        Set<UUID> ticketSeatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1, seatId2), ticketSeatIds);
    }

    @Test
    void constructorShouldCopyTicketsSet() {
        Set<Ticket> originalTickets = new HashSet<>();
        originalTickets.add(new Ticket(seatId1, price1));

        OrderHistory orderHistory = new OrderHistory(orderId, userId, eventId, originalTickets);

        originalTickets.clear();

        Set<Ticket> storedTickets = orderHistory.getTickets();

        assertEquals(Set.of(new Ticket(seatId1, price1)), storedTickets);
    }

    @Test
    void constructorShouldThrowWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(null, userId, eventId, Set.of(new Ticket(seatId1, price1)))
        );
    }

    @Test
    void constructorShouldThrowWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, null, eventId, Set.of(new Ticket(seatId1, price1)))
        );
    }

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, null, Set.of(new Ticket(seatId1, price1)))
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
        tickets.add(new Ticket(seatId1, price1));
        tickets.add(null);

        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, tickets)
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenActiveOrderIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(null, Set.of(new Ticket(seatId1, price1)))
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenTicketsSetIsNull() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId);

        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(activeOrder, null)
        );
    }

    @Test
    void constructorShouldRemoveDuplicateTicketsWithSameSeatIdAndSamePrice() {
        Set<Ticket> tickets = new HashSet<>();
        tickets.add(new Ticket(seatId1, price1));
        tickets.add(new Ticket(seatId1, Money.of("100.00", "ILS")));

        OrderHistory orderHistory = new OrderHistory(orderId, userId, eventId, tickets);

        assertEquals(1, orderHistory.getTickets().size());
        assertEquals(Set.of(new Ticket(seatId1, price1)), orderHistory.getTickets());
    }

    @Test
    void constructorShouldThrowWhenTicketsHaveSameSeatIdButDifferentPrice() {
        Set<Ticket> tickets = new HashSet<>();
        tickets.add(new Ticket(seatId1, price1));
        tickets.add(new Ticket(seatId1, price2));

        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(orderId, userId, eventId, tickets)
        );
    }
}