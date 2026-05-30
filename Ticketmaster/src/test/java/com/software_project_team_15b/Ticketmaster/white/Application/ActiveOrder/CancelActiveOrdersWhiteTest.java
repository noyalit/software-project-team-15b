package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class CancelActiveOrdersWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void cancelAllActiveOrdersOfCurrentUserShouldGetUserOrdersAndCancelEachOne() {
        mockValidUser();

        ActiveOrder order1 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);
        ActiveOrder order2 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);

        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId))
                .thenReturn(List.of(order1, order2));

        service.cancelAllActiveOrdersOfCurrentUser(token);

        verify(purchasingDomainService).getActiveOrdersOfUserForUpdate(userId);
        verify(purchasingDomainService).cancelOrder(order1);
        verify(purchasingDomainService).cancelOrder(order2);
    }

    @Test
    void handleGuestLoggedOutShouldCancelCurrentUsersActiveOrders() {
        mockValidUser();

        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId))
                .thenReturn(List.of());

        service.handleGuestLoggedOut(new GuestLoggedOutEvent(token));

        verify(purchasingDomainService).getActiveOrdersOfUserForUpdate(userId);
    }

    @Test
    void cancelAllActiveOrdersShouldReleaseHoldWhenNeeded() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);

        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId))
                .thenReturn(List.of(order));
        when(purchasingDomainService.shouldReleaseHoldBeforeCancel(order))
                .thenReturn(true);

        service.cancelAllActiveOrdersOfCurrentUser(token);

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).cancelOrder(order);
    }
}
