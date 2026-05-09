package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.concurrency;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateActiveOrderConcurrencyTest extends ConcurrencyTestSupport {

    private UUID userId;
    private UUID eventId;
    private UUID areaId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();

        mockValidUser(userId);

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(queueService.hasAccess(token, eventId))
                .thenReturn(true);
    }

    @Test
    void concurrentCreateActiveOrderShouldCreateOnlyOneActiveOrderForSameUserAndEvent() throws Exception {
        ConcurrencyResult result = runTwoThreads(() ->
                purchasingService.createActiveOrder(token, eventId, areaId)
        );

        List<ActiveOrder> activeOrders =
                activeOrderRepository.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE);

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, activeOrders.size());

        ActiveOrder savedOrder = activeOrders.get(0);
        assertEquals(userId, savedOrder.getUserId());
        assertEquals(eventId, savedOrder.getEventId());
        assertEquals(areaId, savedOrder.getAreaId());
        assertEquals(ActiveOrderStatus.ACTIVE, savedOrder.getStatus());
        assertEquals(Boolean.TRUE, savedOrder.getActiveUniquenessKey());
    }
}