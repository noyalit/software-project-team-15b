package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

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
}