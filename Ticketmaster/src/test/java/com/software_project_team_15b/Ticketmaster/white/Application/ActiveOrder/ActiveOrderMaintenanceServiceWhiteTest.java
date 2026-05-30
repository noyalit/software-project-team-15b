package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.ActiveOrderMaintenanceService;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
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

        @Mock
        private INotifier notifier;

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
                eventDomainService,
                notifier
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
                new ActiveOrderMaintenanceService(null, eventDomainService, notifier)
        );
    }

    @Test
    void constructorShouldThrowWhenEventDomainServiceIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrderMaintenanceService(activeOrderRepository, null, notifier)
        );
    }

    @Test
    void constructorShouldThrowWhenNotifierIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrderMaintenanceService(activeOrderRepository, eventDomainService, null)
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
    void deleteNonActiveOrdersShouldRethrowWhenRepositoryFails() {
        when(activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE))
                .thenThrow(new RuntimeException("database failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.deleteNonActiveOrders()
        );

        assertTrue(exception.getMessage().contains("database failure"));
        verify(activeOrderRepository, never()).deleteAll(any());
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

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldRethrowWhenRepositoryFails() {
        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenThrow(new RuntimeException("scan failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.releaseAndDeleteExpiredActiveOrders()
        );

        assertTrue(exception.getMessage().contains("scan failure"));
        verifyNoInteractions(eventDomainService);
    }

    @Test
    void releaseAndDeleteExpiredActiveOrdersShouldRethrowWhenReleaseFails() {
        ActiveOrder activeOrder = mock(ActiveOrder.class);

        when(activeOrder.getOrderId()).thenReturn(orderId);
        when(activeOrder.getEventId()).thenReturn(eventId);
        when(activeOrder.getOrderSeats()).thenReturn(Set.of(seatId));

        when(activeOrderRepository.findExpiredActiveOrdersForUpdate(
                eq(ActiveOrderStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(activeOrder));

        doThrow(new RuntimeException("release failure"))
                .when(eventDomainService)
                .release(eventId, orderId);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.releaseAndDeleteExpiredActiveOrders()
        );

        assertTrue(exception.getMessage().contains("release failure"));
        verify(activeOrderRepository, never()).delete(activeOrder);
    }

    @Test
    void releaseAndDeleteExpiredActiveOrderShouldRethrowWhenNullOrderIsPassed() throws Exception {
        Method method = ActiveOrderMaintenanceService.class
                .getDeclaredMethod("releaseAndDeleteExpiredActiveOrder", ActiveOrder.class);
        method.setAccessible(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try {
                method.invoke(service, (ActiveOrder) null);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });

        assertTrue(exception.getMessage().contains("Cannot invoke"));
        verifyNoInteractions(eventDomainService, activeOrderRepository);
    }

        @Test
        void notifyCheckoutExpiringSoonShouldNotifyOrdersInsideWarningWindow() {
                ActiveOrder activeOrder = mock(ActiveOrder.class);
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1);
                EventDTO event = new EventDTO(
                        eventId,
                        UUID.randomUUID(),
                        "Concert Night",
                        "Artist",
                        null,
                        null,
                        "Hall",
                        null,
                        List.of()
                );

                when(activeOrder.getOrderId()).thenReturn(orderId);
                when(activeOrder.getUserId()).thenReturn(userId);
                when(activeOrder.getEventId()).thenReturn(eventId);
                when(activeOrder.getExpiresAt()).thenReturn(expiresAt);
                when(activeOrder.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
                when(activeOrder.isCheckoutWarningSent()).thenReturn(false);
                when(activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE))
                                .thenReturn(List.of(activeOrder));
                when(eventDomainService.getEvent(eventId)).thenReturn(event);

                service.notifyCheckoutExpiringSoon();

                verify(notifier).notifyUser(eq(userId), any(NotificationDTO.class));
                verify(activeOrder).markCheckoutWarningSent();
                verify(activeOrderRepository).save(activeOrder);
        }

        @Test
        void notifyCheckoutExpiringSoonShouldSkipOrdersOutsideWarningWindow() {
                ActiveOrder activeOrder = mock(ActiveOrder.class);

                when(activeOrder.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
                when(activeOrder.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
                when(activeOrder.isCheckoutWarningSent()).thenReturn(false);
                when(activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE))
                                .thenReturn(List.of(activeOrder));

                service.notifyCheckoutExpiringSoon();

                verifyNoInteractions(notifier);
                verify(activeOrder, never()).markCheckoutWarningSent();
                verify(activeOrderRepository, never()).save(any());
        }
}
