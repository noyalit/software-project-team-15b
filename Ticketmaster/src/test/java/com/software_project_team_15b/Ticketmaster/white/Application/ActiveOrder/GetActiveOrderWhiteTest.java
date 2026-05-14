package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.DTO.ActiveOrderDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetActiveOrderWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void getActiveOrderShouldReturnViewWhenOrderIsActive() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(Map.of(
                        true, order.getOrderSeats(),
                        false, Set.of()
                ));

        when(purchasingDomainService.syncOrderSeatsAvailability(
                eq(order),
                any()
        )).thenReturn(false);

        mockEventDTOWithCurrentArea();

        when(eventDomainService.getPrice(
                eq(eventId),
                eq(areaId),
                eq(1),
                eq(userId),
                isNull(),
                isNull()
        )).thenReturn(priceBreakdown("0.00"));

        ActiveOrderDTO view = service.getActiveOrder(token, orderId);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsActive(order);
        verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
        verify(queueDomainService).hasAccess(token, eventId);
        verify(eventDomainService).getEvent(eventId);
    }

    @Test
    void getActiveOrderShouldExpireOrderAndThrowWhenTimerExpired() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsActive(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        assertTrue(exception.getMessage().contains("expired"));

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);

        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(eventDomainService, never()).getSeatsAvailability(any(), any(), any());
        verify(eventDomainService, never()).getEvent(any());
    }

    @Test
    void getActiveOrderShouldThrowWhenOrderDoesNotExist() {
        mockValidUser();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenThrow(new IllegalArgumentException("Active order not found: " + orderId));

        assertThrows(IllegalArgumentException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(eventDomainService, never()).getEvent(any());
    }
}
