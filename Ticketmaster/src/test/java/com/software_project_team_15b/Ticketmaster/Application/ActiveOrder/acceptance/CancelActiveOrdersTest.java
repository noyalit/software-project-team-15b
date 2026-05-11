package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancelActiveOrdersTest extends PurchasingServiceTestBase {

    @Test
    void cancelAllActiveOrdersOfCurrentUserShouldCancelAllActiveOrders() {
        mockValidUser();

        ActiveOrder order1 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);
        ActiveOrder order2 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);

        when(activeOrderRepository.findByUserIdAndStatusForUpdate(
                userId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(List.of(order1, order2));

        service.cancelAllActiveOrdersOfCurrentUser(token);

        assertEquals(ActiveOrderStatus.CANCELED, order1.getStatus());
        assertEquals(ActiveOrderStatus.CANCELED, order2.getStatus());

        verify(activeOrderRepository).save(order1);
        verify(activeOrderRepository).save(order2);
    }
}
