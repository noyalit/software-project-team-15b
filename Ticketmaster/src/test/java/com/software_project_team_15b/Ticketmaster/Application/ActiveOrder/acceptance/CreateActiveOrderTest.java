package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.Queue.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateActiveOrderTest extends PurchasingServiceTestBase {

    @Test
    void createActiveOrderShouldSaveNewOrderWhenUserHasAccessAndIsLotteryEligible() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        UUID createdOrderId = service.createActiveOrder(token, eventId, areaId);

        ArgumentCaptor<ActiveOrder> captor = ArgumentCaptor.forClass(ActiveOrder.class);
        verify(activeOrderRepository).saveAndFlush(captor.capture());

        ActiveOrder savedOrder = captor.getValue();

        assertNotNull(createdOrderId);
        assertEquals(createdOrderId, savedOrder.getOrderId());
        assertEquals(userId, savedOrder.getUserId());
        assertEquals(eventId, savedOrder.getEventId());
        assertEquals(areaId, savedOrder.getAreaId());
        assertEquals(ActiveOrderStatus.ACTIVE, savedOrder.getStatus());
        assertEquals(Boolean.TRUE, savedOrder.getActiveUniquenessKey());
        assertNull(savedOrder.getExpiresAt());
    }

    @Test
    void createActiveOrderShouldThrowWhenTokenIsInvalid() {
        mockInvalidUser();

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(auth, never()).extractUserId(any());
        verify(eventManagementService, never()).getEventAvailability(any());
        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenEventIdIsNull() {
        mockValidUser();

        assertThrows(IllegalArgumentException.class, () ->
                service.createActiveOrder(token, null, areaId)
        );

        verify(eventManagementService, never()).getEventAvailability(any());
        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenEventIsNotAvailable() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenAreaIdIsNull() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        assertThrows(IllegalArgumentException.class, () ->
                service.createActiveOrder(token, eventId, null)
        );

        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenAreaIsNotAvailable() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(activeOrderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenUserAlreadyHasActiveOrderForEvent() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(lotteryService, never()).getLotteryEligibilityForEvent(any(), any());
        verify(queueService, never()).hasAccess(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenQueueAccessIsMissing() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        mockLotteryAllowed();
        mockQueueAccessDenied();

        assertThrows(RuntimeException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenLotteryAccessIsMissing() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        LotteryEligibilityResult lotteryEligibilityResult = mock(LotteryEligibilityResult.class);
        when(lotteryEligibilityResult.canCreateActiveOrder()).thenReturn(false);

        when(lotteryService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(lotteryEligibilityResult);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(queueService, never()).hasAccess(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldConvertDataIntegrityViolationToIllegalStateException() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        when(activeOrderRepository.saveAndFlush(any(ActiveOrder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active order"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("User already has an active order"));
    }
}
