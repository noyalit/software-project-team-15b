package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActiveOrderSeatsWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void addSeatsToExistingOrderShouldValidateAccessCheckAvailabilityAndDelegateToDomainService() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);
        Set<UUID> availableSeats = Set.of(seatId1, seatId2);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        LotteryEligibilityDTO eligibility = mockPurchaseAccessAllowed();


        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, availableSeats,
                false, Set.of()
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.requireRequestedSeatsAvailable(
                seatsAvailability,
                requestedSeats
        )).thenReturn(availableSeats);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        service.addSeatsToExistingOrder(token, cmd);

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsModifiable(order);

        verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
        verify(queueDomainService).hasAccess(token, eventId);
        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, true);
        verify(eventDomainService).getSeatsAvailability(eventId, areaId, requestedSeats);
        verify(purchasingDomainService).requireRequestedSeatsAvailable(seatsAvailability, requestedSeats);
        verify(purchasingDomainService).addSeatsToOrder(order, availableSeats);
    }

    @Test
    void addSeatsToExistingOrderShouldThrowWhenCommandIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addSeatsToExistingOrder(token, null)
        );

        verifyNoInteractions(auth);
        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

    @Test
    void addSeatsToExistingOrderShouldThrowWhenOrderIdIsNull() {
        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(null, Set.of(seatId1));

        assertThrows(IllegalArgumentException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        verifyNoInteractions(auth);
        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

    @Test
    void addSeatsToExistingOrderShouldThrowWhenSeatIdsAreEmpty() {
        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of());

        assertThrows(IllegalArgumentException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        verifyNoInteractions(auth);
        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

    @Test
    void addSeatsToExistingOrderShouldStopWhenTokenIsInvalid() {
        mockInvalidUser();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        assertThrows(InvalidTokenException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        verify(auth).isTokenValid(token);
        verify(auth, never()).extractUserId(any());

        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

    @Test
    void addSeatsToExistingOrderShouldExpireOrderWhenOrderIsNoLongerModifiable() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsModifiable(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId2));

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("expired"));

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);

        verify(lotteryDomainService, never()).getLotteryEligibilityForEvent(any(), any());
        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(eventDomainService, never()).getSeatsAvailability(any(), any(), any());
        verify(purchasingDomainService, never()).addSeatsToOrder(any(), any());
    }

        @Test
        void addSeatsToExistingOrderShouldStopWhenPurchaseAccessIsRejected() {
        mockValidUser();

        ActiveOrder order = activeOrder();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        LotteryEligibilityDTO eligibility = mockPurchaseAccessDeniedByQueue();

        doThrow(new TimeExpiredException("User does not have access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        assertThrows(TimeExpiredException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        verify(purchasingDomainService).validateOrderIsModifiable(order);
        verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
        verify(queueDomainService).hasAccess(token, eventId);
        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        verify(eventDomainService, never()).getSeatsAvailability(any(), any(), any());
        verify(purchasingDomainService, never()).addSeatsToOrder(any(), any());
    }

        @Test
        void addSeatsToExistingOrderShouldStopWhenSeatsAreUnavailable() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        LotteryEligibilityDTO eligibility = mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(seatsAvailability);

        doThrow(new IllegalStateException("Some seats are not available"))
                .when(purchasingDomainService)
                .requireRequestedSeatsAvailable(seatsAvailability, requestedSeats);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        assertThrows(IllegalStateException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, true);

        verify(eventDomainService).getSeatsAvailability(eventId, areaId, requestedSeats);
        verify(purchasingDomainService).requireRequestedSeatsAvailable(seatsAvailability, requestedSeats);
        verify(purchasingDomainService, never()).addSeatsToOrder(any(), any());
        }

        @Test
        void removeSeatsFromExistingOrderShouldValidateAccessAndDelegateToDomainService() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        Set<UUID> seatsToRemove = Set.of(seatId1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        LotteryEligibilityDTO eligibility = mockPurchaseAccessAllowed();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, seatsToRemove);

        service.removeSeatsFromExistingOrder(token, cmd);

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsModifiable(order);

        verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
        verify(queueDomainService).hasAccess(token, eventId);
        verify(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, true);

        verify(purchasingDomainService).removeSeatsFromOrder(order, seatsToRemove);

        verify(eventDomainService, never()).getSeatsAvailability(any(), any(), any());
        }

    @Test
    void removeSeatsFromExistingOrderShouldThrowWhenCommandIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                service.removeSeatsFromExistingOrder(token, null)
        );

        verifyNoInteractions(auth);
        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

    @Test
    void removeSeatsFromExistingOrderShouldStopWhenTokenIsInvalid() {
        mockInvalidUser();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        assertThrows(InvalidTokenException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        verify(auth).isTokenValid(token);
        verify(auth, never()).extractUserId(any());

        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(eventDomainService);
        verifyNoInteractions(queueDomainService);
        verifyNoInteractions(lotteryDomainService);
    }

@Test
void removeSeatsFromExistingOrderShouldStopWhenPurchaseAccessIsRejected() {
    mockValidUser();

    ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

    when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
            .thenReturn(order);

    LotteryEligibilityDTO eligibility = mockPurchaseAccessDeniedByQueue();

    doThrow(new TimeExpiredException("User does not have access"))
            .when(purchasingDomainService)
            .requirePurchaseAccess(userId, eventId, eligibility, false);

    RemoveOrAddSeatsFromActiveOrderCommand cmd =
            new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

    assertThrows(TimeExpiredException.class, () ->
            service.removeSeatsFromExistingOrder(token, cmd)
    );

    verify(purchasingDomainService).validateOrderIsModifiable(order);
    verify(lotteryDomainService).getLotteryEligibilityForEvent(userId, eventId);
    verify(queueDomainService).hasAccess(token, eventId);
    verify(purchasingDomainService)
            .requirePurchaseAccess(userId, eventId, eligibility, false);

    verify(purchasingDomainService, never()).removeSeatsFromOrder(any(), any());
}

    @Test
    void removeSeatsFromExistingOrderShouldExpireOrderWhenOrderIsNoLongerModifiable() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsModifiable(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        assertThrows(TimeExpiredException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);

        verify(lotteryDomainService, never()).getLotteryEligibilityForEvent(any(), any());
        verify(queueDomainService, never()).hasAccess(any(), any());
        verify(purchasingDomainService, never()).removeSeatsFromOrder(any(), any());
    }
}