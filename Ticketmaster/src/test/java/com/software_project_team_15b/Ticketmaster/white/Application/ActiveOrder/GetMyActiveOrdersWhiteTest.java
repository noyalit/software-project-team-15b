package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.DTO.ActiveOrderDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetMyActiveOrdersWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void getMyActiveOrdersShouldReturnEmptyListWhenUserHasNoActiveOrders() {
        mockValidUser();
        when(purchasingDomainService.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE))
                .thenReturn(List.of());

        List<ActiveOrderDTO> result = service.getMyActiveOrders(token);

        assertTrue(result.isEmpty());
        verify(purchasingDomainService).findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE);
    }

    @Test
    void getMyActiveOrdersShouldBuildViewForEachActiveOrderWithNoSeats() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        when(purchasingDomainService.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE))
                .thenReturn(List.of(order));

        mockEventDTOWithCurrentArea();

        List<ActiveOrderDTO> result = service.getMyActiveOrders(token);

        assertEquals(1, result.size());
        assertEquals(orderId, result.get(0).orderId());
    }

    @Test
    void getMyActiveOrdersShouldBuildViewForOrderWithSeatsAndNormalPricing() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        when(purchasingDomainService.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE))
                .thenReturn(List.of(order));

        mockEventDTOWithCurrentArea();
        when(eventDomainService.getPrice(eq(eventId), eq(areaId), eq(1), eq(userId), isNull(), isNull()))
                .thenReturn(priceBreakdown("50.00"));

        List<ActiveOrderDTO> result = service.getMyActiveOrders(token);

        assertEquals(1, result.size());
        verify(eventDomainService).getPrice(eventId, areaId, 1, userId, null, null);
    }

    @Test
    void getMyActiveOrdersShouldUseFallbackPricingWhenGetPriceThrows() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        when(purchasingDomainService.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE))
                .thenReturn(List.of(order));

        mockEventDTOWithCurrentArea();
        when(eventDomainService.getPrice(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new RuntimeException("pricing unavailable"));

        List<ActiveOrderDTO> result = service.getMyActiveOrders(token);

        assertEquals(1, result.size());
    }

    @Test
    void requestAccessToCreateActiveOrderShouldReturnQueueAccessDTO() {
        mockValidUser();

        QueueAccessDTO queueAccess = new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.requestAccess(token, eventId)).thenReturn(queueAccess);

        QueueAccessDTO result = service.requestAccessToCreateActiveOrder(token, eventId);

        assertSame(queueAccess, result);
        verify(queueDomainService).requestAccess(token, eventId);
    }
}
