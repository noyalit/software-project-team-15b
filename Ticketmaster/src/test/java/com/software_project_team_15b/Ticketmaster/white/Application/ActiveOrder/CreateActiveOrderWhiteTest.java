package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateActiveOrderWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void createActiveOrderShouldValidateAccessAndDelegateCreationToDomainService() {
        mockValidUser();

        UUID createdOrderId = UUID.randomUUID();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);
        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(true);

        when(purchasingDomainService.createActiveOrder(userId, eventId, areaId))
                .thenReturn(createdOrderId);

        UUID result = service.createActiveOrder(token, eventId, areaId);

        assertEquals(createdOrderId, result);

        verify(auth).isTokenValid(token);
        verify(auth).extractUserId(token);

        verify(eventDomainService).getEventAvailability(eventId);
        verify(purchasingDomainService)
                .requireEventCanBeBooked(eventId, EventAvailability.AVAILABLE);

        verify(eventDomainService).getAreaAvailability(eventId, areaId);
        verify(purchasingDomainService)
                .requireAreaCanBeBooked(areaId, true);

        verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
        verify(queueDomainService).hasAccess(token, eventId);
        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, true);

        verify(purchasingDomainService).createActiveOrder(userId, eventId, areaId);
    }

    @Test
    void createActiveOrderShouldThrowWhenTokenIsInvalid() {
        mockInvalidUser();

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(auth).isTokenValid(token);
        verify(auth, never()).extractUserId(any());

        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
        verifyNoInteractions(purchasingDomainService);
    }

    @Test
    void createActiveOrderShouldStopWhenEventCannotBeBooked() {
        mockValidUser();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(null);

        doThrow(new IllegalStateException("Event is not available for booking"))
                .when(purchasingDomainService)
                .requireEventCanBeBooked(eventId, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("Event is not available"));

        verify(eventDomainService).getEventAvailability(eventId);
        verify(purchasingDomainService).requireEventCanBeBooked(eventId, null);

        verify(eventDomainService, never()).getAreaAvailability(any(), any());
        verify(lotteryDomainService, never()).getLotteryEligibilityForEvent(any(), any());
        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(purchasingDomainService, never()).createActiveOrder(any(), any(), any());
    }

    @Test
    void createActiveOrderShouldStopWhenAreaCannotBeBooked() {
        mockValidUser();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(false);

        doThrow(new IllegalStateException("Area is not available for booking"))
                .when(purchasingDomainService)
                .requireAreaCanBeBooked(areaId, false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("Area is not available"));

        verify(eventDomainService).getEventAvailability(eventId);
        verify(purchasingDomainService)
                .requireEventCanBeBooked(eventId, EventAvailability.AVAILABLE);

        verify(eventDomainService).getAreaAvailability(eventId, areaId);
        verify(purchasingDomainService).requireAreaCanBeBooked(areaId, false);

        verify(lotteryDomainService, never()).getLotteryEligibilityForEvent(any(), any());
        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(purchasingDomainService, never()).createActiveOrder(any(), any(), any());
    }

    @Test
    void createActiveOrderShouldStopWhenPurchaseAccessIsRejected() {
        mockValidUser();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(false);

        doThrow(new IllegalStateException("User is not eligible or has no queue access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        verify(purchasingDomainService, never())
                .createActiveOrder(any(), any(), any());
    }

    @Test
    void createActiveOrderShouldConvertDataIntegrityViolationToIllegalStateException() {
        mockValidUser();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(true);

        when(purchasingDomainService.createActiveOrder(userId, eventId, areaId))
                .thenThrow(new DataIntegrityViolationException("duplicate active order"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("User already has an active order"));

        verify(purchasingDomainService).createActiveOrder(userId, eventId, areaId);
    }
}