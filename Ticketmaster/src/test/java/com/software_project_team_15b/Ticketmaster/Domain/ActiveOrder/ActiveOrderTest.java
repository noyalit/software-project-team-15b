package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.UnactiveOrderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActiveOrderTest {

    private UUID orderId;
    private UUID userId;
    private UUID eventId;
    private UUID seatId1;
    private UUID seatId2;
    private UUID areaId;

    private ActiveOrder order;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();
        areaId = UUID.randomUUID();

        now = LocalDateTime.now();

        order = new ActiveOrder(orderId, userId, eventId, areaId);
    }

    // ---------- CONSTRUCTOR ----------

    @Test
    void constructor_shouldCreateOrder_whenValidDataGiven() {
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(eventId, order.getEventId());
        assertEquals(areaId, order.getAreaId());
        assertEquals(Boolean.TRUE, order.getActiveUniquenessKey());
        assertEquals(ActiveOrderStatus.ACTIVE, order.getStatus());
    }

    @Test
    void constructor_shouldThrowException_whenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(null, userId, eventId, areaId));
    }

    @Test
    void constructor_shouldThrowException_whenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(orderId, null, eventId, areaId));
    }

    @Test
    void constructor_shouldThrowException_whenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(orderId, userId, null, areaId));
    }

    @Test
    void constructor_shouldThrowException_whenAreaIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(orderId, userId, eventId, null));
    }

    // ---------- SEATS ----------

    @Test
    void addSeats_shouldThrowException_whenSeatIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> order.addSeats(null));
    }

    @Test
    void removeSeats_shouldThrowException_whenSeatIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> order.removeSeats(null));
    }
    
    
    @Test
    void addSeats_shouldAddSeat_whenSeatIsValid() {
        order.addSeats(Set.of(seatId1));

        assertTrue(order.getOrderSeats().contains(seatId1));
    }

    @Test
    void addSeats_shouldThrowException_whenSeatAlreadyExists() {
        order.addSeats(Set.of(seatId1));

        assertThrows(AlreadyDoneException.class,
                () -> order.addSeats(Set.of(seatId1)));
    }

    @Test
    void removeSeats_shouldRemoveSeat_whenSeatExists() {
        order.addSeats(Set.of(seatId1));
        order.removeSeats(Set.of(seatId1));

        assertFalse(order.getOrderSeats().contains(seatId1));
    }

    @Test
    void removeSeats_shouldThrowException_whenSeatNotFound() {
        order.addSeats(Set.of(seatId1));
        assertThrows(AlreadyDoneException.class,
                () -> order.removeSeats(Set.of(seatId2)));
    }

  
    @Test
    void getOrderSeats_shouldReturnUnmodifiableCopy() {
        order.addSeats(Set.of(seatId1));

        Set<UUID> seats = order.getOrderSeats();

        assertThrows(UnsupportedOperationException.class,
            () -> seats.add(seatId2));
    }

    @Test
    void addSeat_shouldThrowTimeExpiredException_whenOrderExpired() {
        ActiveOrder expiredOrder = new ActiveOrder(
        orderId,
        userId,
        eventId,
        now.minusMinutes(11),
        now.minusMinutes(1)
        );

        assertThrows(TimeExpiredException.class,
            () -> expiredOrder.addSeats(Set.of(seatId1)));
    }
    

    // ---------- STATUS -----------

    @Test
    void complete_shouldSetStatusToCompleted_whenOrderIsActive() {
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));
        order.complete();

        assertEquals(ActiveOrderStatus.COMPLETED, order.getStatus());
        assertNull(order.getActiveUniquenessKey());
    }

    @Test
    void cancel_shouldSetStatusToCanceled_whenOrderIsActive() {
        order.addSeats(Set.of(seatId1));
        order.cancel();

        assertEquals(ActiveOrderStatus.CANCELED, order.getStatus());
        assertNull(order.getActiveUniquenessKey());
    }

    @Test
    void addSeats_shouldThrowUnactiveOrderException_whenOrderIsCompleted() {
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));
        order.complete();

        assertThrows(UnactiveOrderException.class,
            () -> order.addSeats(Set.of(seatId2)));
    }

    @Test
    void complete_shouldThrowUnactiveOrderException_whenOrderAlreadyCompleted() {
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));
        order.complete();

        assertThrows(UnactiveOrderException.class,
            order::complete);
    }

    @Test
    void cancel_shouldThrowUnactiveOrderException_whenOrderAlreadyCanceled() {
        order.addSeats(Set.of(seatId1));
        order.cancel();

        assertThrows(UnactiveOrderException.class,
            order::cancel);
    }

    @Test
    void cancel_shouldThrowUnactiveOrderException_whenOrderIsCompleted() {
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));
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
        assertNull(expiredOrder.getActiveUniquenessKey());
    }


    @Test
    void expire_shouldThrowRuntimeException_whenOrderHasNotExpired() {
        assertThrows(RuntimeException.class, order::expire);
    }

    @Test
    void expire_shouldThrowUnactiveOrderException_whenOrderIsNotActive() {
        order.addSeats(Set.of(seatId1));
        order.cancel(); 

        assertThrows(UnactiveOrderException.class, order::expire);
    }
        
}
