package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.UnactiveOrderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

public class ActiveOrderTest {

    private UUID orderId;
    private UUID userId;
    private UUID eventId;
    private UUID seatId1;
    private UUID seatId2;

    private ActiveOrder order;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        now = LocalDateTime.now();

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
    void addSeat_shouldThrowException_whenSeatIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> order.addSeat(null));
    }

    @Test
    void removeSeat_shouldThrowException_whenSeatIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> order.removeSeat(null));
    }
    
    
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
    @Disabled("This test will fail until issue #5 is fixed and merged")
    void removeSeat_shouldCancelOrder_whenLastSeatRemoved() {
        order.addSeat(seatId1);
        order.removeSeat(seatId1);

        assertEquals(ActiveOrderStatus.CANCELED, order.getStatus());
    }

    @Test
    void getOrderSeats_shouldReturnUnmodifiableCopy() {
        order.addSeat(seatId1);

        Set<UUID> seats = order.getOrderSeats();

        assertThrows(UnsupportedOperationException.class,
            () -> seats.add(seatId2));
    }

    @Test
    @Disabled("Enable after PR introduces TimeExpiredException")
    void addSeat_shouldThrowTimeExpiredException_whenOrderExpired() {
        ActiveOrder expiredOrder = new ActiveOrder(
        orderId,
        userId,
        eventId,
        now.minusMinutes(11),
        now.minusMinutes(1)
        );

        assertThrows(TimeExpiredException.class,
            () -> expiredOrder.addSeat(seatId1));
    }
    

    // ---------- STATUS -----------

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
    @Disabled("Enable after issue #5 is fixed and merged")
    void addSeat_shouldThrowUnactiveOrderException_whenOrderIsCompleted() {
        order.addSeat(seatId1);
        order.complete();

        assertThrows(UnactiveOrderException.class,
            () -> order.addSeat(seatId2));
    }

    @Test
    @Disabled("Enable after issue #5 is fixed and merged")
    void complete_shouldThrowUnactiveOrderException_whenOrderAlreadyCompleted() {
        order.addSeat(seatId1);
        order.complete();

        assertThrows(UnactiveOrderException.class,
            order::complete);
    }

    @Test
    @Disabled("Enable after issue #5 is fixed and merged")
    void cancel_shouldThrowUnactiveOrderException_whenOrderAlreadyCanceled() {
        order.addSeat(seatId1);
        order.cancel();

        assertThrows(UnactiveOrderException.class,
            order::cancel);
    }

    @Test
    void cancel_shouldThrowUnactiveOrderException_whenOrderIsCompleted() {
        order.addSeat(seatId1);
        order.complete();

        assertThrows(UnactiveOrderException.class, order::cancel);
    }

    // ---------- EXPIRATION ----------

    @Test
    void hasTimeExpired_shouldReturnFalse_whenOrderIsNew() {
        assertFalse(order.hasTimeExpired());
    }

    @Test
    void expire_shouldSetStatusToExpired_whenTimeHasPassed() {
        LocalDateTime now = LocalDateTime.now();
        ActiveOrder expiredOrder = new ActiveOrder(
        orderId,
        userId,
        eventId,
        now.minusMinutes(11),
        now.minusMinutes(1)
    );

        expiredOrder.expire();

        assertEquals(ActiveOrderStatus.EXPIRED, expiredOrder.getStatus());
    }


    @Test
    @Disabled("Enable after expiration rules are finalized")
    void expire_shouldThrowRuntimeException_whenOrderHasNotExpired() {
        assertThrows(RuntimeException.class, order::expire);
    }

    @Test
    @Disabled("Enable after expiration rules are finalized")
    void expire_shouldThrowUnactiveOrderException_whenOrderIsNotActive() {
        order.addSeat(seatId1);
        order.cancel(); 

        assertThrows(UnactiveOrderException.class, order::expire);
    }
        
}
