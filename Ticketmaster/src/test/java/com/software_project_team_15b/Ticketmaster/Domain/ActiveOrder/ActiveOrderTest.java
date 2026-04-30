package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActiveOrderTest {

    private UUID orderId;
    private UUID userId;
    private UUID eventId;
    private UUID seatId1;
    private UUID seatId2;

    private ActiveOrder order;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        order = new ActiveOrder(orderId, userId, eventId);
    }

    // ---------- CONSTRUCTOR ----------

    @Test
    void constructor_shouldCreateOrder_whenValidDataGiven() {
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(eventId, order.getEventId());
        assertEquals(ActiveOrderStatus.ACTIVE, order.getStatus());
    }

    @Test
    void constructor_shouldThrowException_whenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(null, userId, eventId));
    }

    @Test
    void constructor_shouldThrowException_whenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(orderId, null, eventId));
    }

    @Test
    void constructor_shouldThrowException_whenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(orderId, userId, null));
    }

    // ---------- SEATS ----------

    @Test
    void addSeat_shouldAddSeat_whenSeatIsValid() {
        order.addSeat(seatId1);

        assertTrue(order.getOrderSeats().contains(seatId1));
    }

    @Test
    void addSeat_shouldThrowException_whenSeatAlreadyExists() {
        order.addSeat(seatId1);

        assertThrows(IllegalArgumentException.class,
                () -> order.addSeat(seatId1));
    }

    @Test
    void removeSeat_shouldRemoveSeat_whenSeatExists() {
        order.addSeat(seatId1);
        order.removeSeat(seatId1);

        assertFalse(order.getOrderSeats().contains(seatId1));
    }

    @Test
    void removeSeat_shouldThrowException_whenSeatNotFound() {
        order.addSeat(seatId1);
        assertThrows(IllegalArgumentException.class,
                () -> order.removeSeat(seatId2));
    }

    @Test
    void removeSeat_shouldCancelOrder_whenLastSeatRemoved() {
        order.addSeat(seatId1);
        order.removeSeat(seatId1);

        assertEquals(ActiveOrderStatus.CANCELED, order.getStatus());
    }

    // ---------- STATUS ----------

    @Test
    void complete_shouldSetStatusToCompleted_whenOrderIsActive() {
        order.addSeat(seatId1);
        order.complete();

        assertEquals(ActiveOrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void cancel_shouldSetStatusToCanceled_whenOrderIsActive() {
        order.addSeat(seatId1);
        order.cancel();

        assertEquals(ActiveOrderStatus.CANCELED, order.getStatus());
    }

    @Test
    void addSeat_shouldThrowException_whenOrderIsCompleted() {
        order.addSeat(seatId1);
        order.complete();

        assertThrows(Exception.class,
            () -> order.addSeat(seatId2));
    }

    // ---------- EXPIRATION ----------

    @Test
    void hasTimeExpired_shouldReturnFalse_whenOrderIsNew() {
        assertFalse(order.hasTimeExpired());
    }

    @Test
    void expire_shouldSetStatusToExpired_whenOrderHasExpired() {
        ActiveOrder expiredOrder = new ActiveOrder(orderId, userId, eventId) {
            @Override
            public boolean hasTimeExpired() {
                return true;
            }
        };

        expiredOrder.expire();

        assertEquals(ActiveOrderStatus.EXPIRED, expiredOrder.getStatus());
    }

    @Test
    void expire_shouldThrowException_whenOrderHasNotExpired() {
        assertThrows(Exception.class, order::expire);
    }
}
