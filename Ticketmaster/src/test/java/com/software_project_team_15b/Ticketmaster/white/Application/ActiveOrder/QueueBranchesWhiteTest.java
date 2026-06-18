package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueBranchesWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void createActiveOrderShouldThrowWhenQueueIsActiveAndUserIsWaiting() {
        mockValidUser();

        QueueAccessDTO waitingAccess = new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, 2, null);
        when(queueDomainService.requestAccess(token, eventId)).thenReturn(waitingAccess);

        TimeExpiredException ex = assertThrows(TimeExpiredException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(ex.getMessage().contains("position: 3"));
        verify(purchasingDomainService, never()).createActiveOrder(any(), any(), any());
    }

    @Test
    void createActiveOrderShouldThrowWhenQueueCapacityIsReached() {
        mockValidUser();

        QueueAccessDTO admittedAccess = new QueueAccessDTO(eventId, QueueAccessStatus.ADMITTED, null,
                java.time.LocalDateTime.now().plusMinutes(10));
        when(queueDomainService.requestAccess(token, eventId)).thenReturn(admittedAccess);

        QueueSnapshotDTO snapshot = new QueueSnapshotDTO(eventId, 10, 1, 0, 1, Map.of());
        when(queueDomainService.getQueueSnapshot(eventId)).thenReturn(snapshot);
        when(purchasingDomainService.countActiveOrdersForEvent(eventId)).thenReturn(1L);

        assertThrows(TimeExpiredException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(purchasingDomainService, never()).createActiveOrder(any(), any(), any());
    }

    @Test
    void createActiveOrderShouldProceedWhenQueueAdmittedAndCapacityNotReached() {
        mockValidUser();

        QueueAccessDTO admittedAccess = new QueueAccessDTO(eventId, QueueAccessStatus.ADMITTED, null,
                java.time.LocalDateTime.now().plusMinutes(10));
        when(queueDomainService.requestAccess(token, eventId)).thenReturn(admittedAccess);

        QueueSnapshotDTO snapshot = new QueueSnapshotDTO(eventId, 10, 5, 0, 2, Map.of());
        when(queueDomainService.getQueueSnapshot(eventId)).thenReturn(snapshot);
        when(purchasingDomainService.countActiveOrdersForEvent(eventId)).thenReturn(2L);

        when(eventDomainService.getEventAvailability(eventId)).thenReturn(EventAvailability.AVAILABLE);
        when(eventDomainService.getAreaAvailability(eventId, areaId)).thenReturn(true);

        when(queueDomainService.hasAccess(token, eventId)).thenReturn(true);
        when(purchasingDomainService.createActiveOrder(userId, eventId, areaId)).thenReturn(orderId);

        assertEquals(orderId, service.createActiveOrder(token, eventId, areaId));
    }

    @Test
    void requireAccessForPurchaseShouldThrowTimeExpiredWithQueuePositionWhenWaiting() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);

        when(queueDomainService.hasAccess(token, eventId)).thenReturn(false);

        doThrow(new TimeExpiredException("no access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(eq(userId), eq(eventId), any(), eq(false));

        QueueAccessDTO waitingView = new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.getQueueAccessView(token, eventId)).thenReturn(waitingView);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        TimeExpiredException ex = assertThrows(TimeExpiredException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(ex.getMessage().contains("Please join the queue"));
    }

    @Test
    void requireAccessForPurchaseShouldThrowTimeExpiredWithJoinQueueMessageWhenViewAccessThrows() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);

        when(queueDomainService.hasAccess(token, eventId)).thenReturn(false);

        doThrow(new TimeExpiredException("no access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(eq(userId), eq(eventId), any(), eq(false));

        when(queueDomainService.getQueueAccessView(token, eventId))
                .thenThrow(new RuntimeException("queue error"));

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(ex.getMessage().contains("queue error"));
    }
}
