package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.ActiveOrderView;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetActiveOrderTest extends PurchasingServiceTestBase {

    @Test
    void getActiveOrderShouldReturnViewWhenOrderIsActive() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        when(eventManagementService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(Map.of(
                        true, order.getOrderSeats(),
                        false, Set.of()
                ));

        mockEventViewWithCurrentArea();

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown("0.00"));

        ActiveOrderView view = service.getActiveOrder(token, orderId);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());

        verify(eventManagementService).getEvent(eventId);
    }

    @Test
    void getActiveOrderShouldExpireAndThrowWhenTimerExpired() {
        mockValidUser();

        ActiveOrder order = mockExpiredActiveOrder();

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        assertTrue(exception.getMessage().contains("expired"));

        verify(eventManagementService).release(eventId, orderId);
        verify(order).expire();
        verify(activeOrderRepository).save(order);

        verify(queueService, never()).hasAccess(any(), any());
        verify(eventManagementService, never()).getSeatsAvailability(any(), any(), any());
        verify(eventManagementService, never()).getEvent(any());
    }

    @Test
    void getActiveOrderShouldThrowWhenOrderDoesNotExist() {
        mockValidUser();

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        verify(queueService, never()).hasAccess(any(), any());
        verify(eventManagementService, never()).getEvent(any());
    }
}
