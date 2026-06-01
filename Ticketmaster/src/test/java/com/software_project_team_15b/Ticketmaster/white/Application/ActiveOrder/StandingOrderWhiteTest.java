package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StandingOrderWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void addStandingQuantityToExistingOrderShouldThrowWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addStandingQuantityToExistingOrder(token, null, 1)
        );

        verifyNoInteractions(auth, purchasingDomainService);
    }

    @Test
    void addStandingQuantityToExistingOrderShouldThrowWhenQuantityIsLessThanOne() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addStandingQuantityToExistingOrder(token, orderId, 0)
        );

        verifyNoInteractions(auth, purchasingDomainService);
    }

    @Test
    void addStandingQuantityToExistingOrderShouldAddSeatsSuccessfully() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        Set<UUID> selectedSeats = Set.of(seatId1);
        when(eventDomainService.selectAvailableStandingSeats(eventId, areaId, Set.of(), 1))
                .thenReturn(selectedSeats);

        service.addStandingQuantityToExistingOrder(token, orderId, 1);

        verify(purchasingDomainService).addSeatsToOrder(order, selectedSeats);
    }

    @Test
    void addStandingQuantityToExistingOrderShouldExpireOrderWhenTimerExpired() {
        mockValidUser();

        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);
        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService).validateOrderIsModifiable(order);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order)).thenReturn(true);

        assertThrows(TimeExpiredException.class, () ->
                service.addStandingQuantityToExistingOrder(token, orderId, 1)
        );

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);
        verify(purchasingDomainService, never()).addSeatsToOrder(any(), any());
    }

    @Test
    void removeStandingQuantityFromExistingOrderShouldThrowWhenQuantityIsLessThanOne() {
        assertThrows(IllegalArgumentException.class, () ->
                service.removeStandingQuantityFromExistingOrder(token, orderId, 0)
        );

        verifyNoInteractions(auth, purchasingDomainService);
    }

    @Test
    void removeStandingQuantityFromExistingOrderShouldRemoveSeatsSuccessfully() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        service.removeStandingQuantityFromExistingOrder(token, orderId, 1);

        verify(purchasingDomainService).removeSeatsFromOrder(eq(order), any());
    }

    @Test
    void removeStandingQuantityFromExistingOrderShouldThrowWhenNotEnoughTickets() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        assertThrows(IllegalArgumentException.class, () ->
                service.removeStandingQuantityFromExistingOrder(token, orderId, 2)
        );

        verify(purchasingDomainService, never()).removeSeatsFromOrder(any(), any());
    }

    @Test
    void removeStandingQuantityFromExistingOrderShouldExpireOrderWhenTimerExpired() {
        mockValidUser();

        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);
        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService).validateOrderIsModifiable(order);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order)).thenReturn(true);

        assertThrows(TimeExpiredException.class, () ->
                service.removeStandingQuantityFromExistingOrder(token, orderId, 1)
        );

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);
        verify(purchasingDomainService, never()).removeSeatsFromOrder(any(), any());
    }
}
