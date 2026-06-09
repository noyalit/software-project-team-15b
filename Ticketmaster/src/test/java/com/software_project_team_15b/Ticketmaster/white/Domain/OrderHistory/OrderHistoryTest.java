package com.software_project_team_15b.Ticketmaster.white.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;

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
    private UUID areaId;
    private UUID seatId1;
    private UUID seatId2;

    private Integer paymentTransactionId;

    private String externalTicketId;

    private Money basePricePerTicket;
    private Money differentPrice;
    private Money totalPrice;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();

        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        paymentTransactionId = 12345;

        externalTicketId = "TICKET-" + UUID.randomUUID();

        basePricePerTicket = Money.of("100.00", "ILS");
        differentPrice = Money.of("150.00", "ILS");
        totalPrice = Money.of("180.00", "ILS");
    }

    @Test
    void fromActiveOrderShouldCopyFieldsAndCreateTickets() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1, seatId2));

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(
                activeOrder,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                basePricePerTicket
        );

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());
        assertEquals(areaId, orderHistory.getAreaId());
        assertEquals(paymentTransactionId, orderHistory.getPaymentTransactionId());
        assertEquals(totalPrice, orderHistory.getTotalPrice());

        Set<Ticket> expectedTickets = Set.of(
                new Ticket(seatId1, basePricePerTicket),
                new Ticket(seatId2, basePricePerTicket)
        );

        assertEquals(expectedTickets, orderHistory.getTickets());
    }

    @Test
    void constructorShouldInitializeFieldsCorrectly() {
        Set<Ticket> tickets = Set.of(
                new Ticket(seatId1, basePricePerTicket),
                new Ticket(seatId2, differentPrice)
        );

        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                areaId,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                tickets
        );

        assertEquals(orderId, orderHistory.getOrderId());
        assertEquals(userId, orderHistory.getUserId());
        assertEquals(eventId, orderHistory.getEventId());
        assertEquals(areaId, orderHistory.getAreaId());
        assertEquals(paymentTransactionId, orderHistory.getPaymentTransactionId());
        assertEquals(externalTicketId, orderHistory.getTicketIdentifier());
        assertEquals(totalPrice, orderHistory.getTotalPrice());
        assertEquals(tickets, orderHistory.getTickets());
    }

    @Test
    void constructorShouldInitializeOrderAsNotCancelled() {
        OrderHistory orderHistory = createSingleTicketOrderHistory();

        assertFalse(orderHistory.isCancelled());
    }

    @Test
    void cancelShouldMarkOrderAsCancelled() {
        OrderHistory orderHistory = createSingleTicketOrderHistory();

        orderHistory.cancel();

        assertTrue(orderHistory.isCancelled());
    }

    @Test
    void cancelShouldThrowWhenOrderAlreadyCancelled() {
        OrderHistory orderHistory = createSingleTicketOrderHistory();

        orderHistory.cancel();

        assertThrows(IllegalStateException.class, orderHistory::cancel);
    }

    @Test
    void getTicketsShouldReturnUnmodifiableSet() {
        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                areaId,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                Set.of(
                        new Ticket(seatId1, basePricePerTicket),
                        new Ticket(seatId2, differentPrice)
                )
        );

        Set<Ticket> returnedTickets = orderHistory.getTickets();

        assertThrows(UnsupportedOperationException.class, () ->
                returnedTickets.add(new Ticket(UUID.randomUUID(), basePricePerTicket))
        );
    }

    @Test
    void getTicketsShouldProtectInternalState() {
        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                areaId,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                Set.of(
                        new Ticket(seatId1, basePricePerTicket),
                        new Ticket(seatId2, differentPrice)
                )
        );

        Set<Ticket> returnedTickets = orderHistory.getTickets();

        assertThrows(UnsupportedOperationException.class, () ->
                returnedTickets.clear()
        );

        Set<UUID> ticketSeatIds = orderHistory.getTickets().stream()
                .map(Ticket::getSeatId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(seatId1, seatId2), ticketSeatIds);
    }

    @Test
    void getTotalPriceShouldReturnDefensiveCopy() {
        OrderHistory orderHistory = createSingleTicketOrderHistory();

        Money returnedTotalPrice = orderHistory.getTotalPrice();

        assertEquals(totalPrice, returnedTotalPrice);
        assertNotSame(totalPrice, returnedTotalPrice);
    }

    @Test
    void constructorShouldCopyTotalPrice() {
        OrderHistory orderHistory = createSingleTicketOrderHistory();

        Money returnedTotalPrice = orderHistory.getTotalPrice();

        assertNotSame(totalPrice, returnedTotalPrice);
        assertEquals(totalPrice.amount(), returnedTotalPrice.amount());
        assertEquals(totalPrice.currency(), returnedTotalPrice.currency());
    }

    @Test
    void constructorShouldCopyTicketsSet() {
        Set<Ticket> originalTickets = new HashSet<>();
        originalTickets.add(new Ticket(seatId1, basePricePerTicket));

        OrderHistory orderHistory = new OrderHistory(
                orderId,
                userId,
                eventId,
                areaId,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                originalTickets
        );

        originalTickets.clear();

        assertEquals(
                Set.of(new Ticket(seatId1, basePricePerTicket)),
                orderHistory.getTickets()
        );
    }

    @Test
    void constructorShouldThrowWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        null,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        null,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        null,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenAreaIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        null,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenPaymentTransactionIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        null,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

       @Test
    void constructorShouldThrowWhenTicketIdentifierIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        null,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenTotalPriceIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        null,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsSetIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        null
                )
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsSetIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        Set.of()
                )
        );
    }
    
    @Test
    void constructorShouldThrowWhenPaymentTransactionIdIsNonPositive() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        0,
                        externalTicketId,
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

        @Test
    void constructorShouldThrowWhenTicketIdentifierIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        10000,
                        "",
                        totalPrice,
                        Set.of(new Ticket(seatId1, basePricePerTicket))
                )
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsContainNull() {
        Set<Ticket> tickets = new HashSet<>();
        tickets.add(new Ticket(seatId1, basePricePerTicket));
        tickets.add(null);

        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        tickets
                )
        );
    }

    @Test
    void constructorShouldThrowWhenTicketsHaveSameSeatId() {
        Set<Ticket> tickets = new HashSet<>();
        tickets.add(new Ticket(seatId1, basePricePerTicket));
        tickets.add(new Ticket(seatId1, differentPrice));

        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistory(
                        orderId,
                        userId,
                        eventId,
                        areaId,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        tickets
                )
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenActiveOrderIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(
                        null,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        basePricePerTicket
                )
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenPaymentTransactionIdIsNull() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1));

        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(
                        activeOrder,
                        null,
                        externalTicketId,
                        totalPrice,
                        basePricePerTicket
                )
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenTotalPriceIsNull() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1));

        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(
                        activeOrder,
                        paymentTransactionId,
                        externalTicketId,
                        null,
                        basePricePerTicket
                )
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenBasePricePerTicketIsNull() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1));

        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(
                        activeOrder,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        null
                )
        );
    }

    @Test
    void fromActiveOrderShouldThrowWhenActiveOrderHasNoSeats() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);

        assertThrows(IllegalArgumentException.class, () ->
                OrderHistory.fromActiveOrder(
                        activeOrder,
                        paymentTransactionId,
                        externalTicketId,
                        totalPrice,
                        basePricePerTicket
                )
        );
    }

    private OrderHistory createSingleTicketOrderHistory() {
        return new OrderHistory(
                orderId,
                userId,
                eventId,
                areaId,
                paymentTransactionId,
                externalTicketId,
                totalPrice,
                Set.of(new Ticket(seatId1, basePricePerTicket))
        );
    }

}