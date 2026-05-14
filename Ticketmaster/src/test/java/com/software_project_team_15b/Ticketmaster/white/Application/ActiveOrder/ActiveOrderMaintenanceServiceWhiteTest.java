package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.ActiveOrderMaintenanceService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveOrderMaintenanceServiceWhiteTest {

    @Mock
    private IActiveOrderRepository activeOrderRepository;

    @Mock
    private IEventDomainService eventDomainService;

    private ActiveOrderMaintenanceService service;

    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        service = new ActiveOrderMaintenanceService(
                activeOrderRepository,
                eventDomainService
        );

        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId = UUID.randomUUID();
    }

    @Test
    void constructorShouldThrowWhenRepositoryIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrderMaintenanceService(null, eventDomainService)
        );
    }

    @Test
    void constructorShouldThrowWhenEventDomainServiceIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrderMaintenanceService(activeOrderRepository, null)
        );
    }

    @Test
    void deleteNonActiveOrdersShouldDoNothingWhenNoOrdersFound() {
        when(activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE))
                .thenReturn(List.of());

        service.deleteNonActiveOrders();

        verify(activeOrderRepository).findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE);
        verify(activeOrderRepository, never()).deleteAll(any());
    }

    @Test
    void deleteNonActiveOrdersShouldDeleteFoundOrders() {
        ActiveOrder canceledOrder = new ActiveOrder(
                UUID.randomUUID(),
                userId,
                eventId,
                areaId
        );
        canceledOrder.cancel();

        ActiveOrder expiredOrder = mock(ActiveOrder.class);

        List<ActiveOrder> ordersToDelete = List.of(canceledOrder, expiredOrder);

        when(activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE))
                .thenReturn(ordersToDelete);

        service.deleteNonActiveOrders();

        verify(activeOrderRepository).deleteAll(ordersToDelete);
    }

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldDoNothingWhenNoOrdersFound() {
        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        service.releaseAndDeleteExpiredActiveOrders();

        verify(activeOrderRepository).findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        );

        verifyNoInteractions(eventDomainService);
        verify(activeOrderRepository, never()).delete(any());
    }

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldReleaseAndDeleteOrderWithSeats() {
        ActiveOrder activeOrder = mock(ActiveOrder.class);

        when(activeOrder.getOrderId()).thenReturn(orderId);
        when(activeOrder.getEventId()).thenReturn(eventId);
        when(activeOrder.getOrderSeats()).thenReturn(Set.of(seatId));

        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(activeOrder));

        service.releaseAndDeleteExpiredActiveOrders();

        verify(eventDomainService).release(eventId, orderId);
        verify(activeOrderRepository).delete(activeOrder);
    }

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldDeleteWithoutReleaseWhenOrderHasNoSeats() {
        ActiveOrder activeOrder = mock(ActiveOrder.class);

        when(activeOrder.getOrderId()).thenReturn(orderId);
        when(activeOrder.getEventId()).thenReturn(eventId);
        when(activeOrder.getOrderSeats()).thenReturn(Set.of());

        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(activeOrder));

        service.releaseAndDeleteExpiredActiveOrders();

        verify(eventDomainService, never()).release(any(), any());
        verify(activeOrderRepository).delete(activeOrder);
    }

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldQueryOrdersExpiredAtLeastOneMinuteAgo() {
        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        LocalDateTime beforeCall = LocalDateTime.now().minusMinutes(1).minusSeconds(2);

        service.releaseAndDeleteExpiredActiveOrders();

        LocalDateTime afterCall = LocalDateTime.now().minusMinutes(1).plusSeconds(2);

        ArgumentCaptor<LocalDateTime> captor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(activeOrderRepository).findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                captor.capture()
        );

        LocalDateTime expiredBefore = captor.getValue();

        assertTrue(expiredBefore.isAfter(beforeCall));
        assertTrue(expiredBefore.isBefore(afterCall));
    }
}