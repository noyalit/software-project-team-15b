package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.concurrency;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartCheckoutConcurrencyTest extends ConcurrencyTestSupport {

    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        mockValidUser(userId);

        when(queueService.hasAccess(token, eventId))
                .thenReturn(true);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, Set.of(seatId)))
                .thenReturn(Map.of(
                        true, Set.of(seatId),
                        false, Set.of()
                ));

        doNothing().when(eventManagementService)
                .validatePurchaseEligibility(eq(eventId), any());

        HoldReceipt holdReceipt = mock(HoldReceipt.class);
        when(eventManagementService.hold(eq(eventId), any()))
                .thenReturn(holdReceipt);

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId));
        activeOrderRepository.save(order);
    }

    @Test
    void concurrentStartCheckoutShouldHoldSeatsOnlyOnce() throws Exception {
        ConcurrencyResult result = runTwoThreads(() ->
                purchasingService.startCheckoutForGuest(
                        token,
                        orderId,
                        LocalDate.of(2000, 1, 1)
                )
        );

        ActiveOrder updatedOrder = activeOrderRepository.findById(orderId).orElseThrow();

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());

        Throwable failure = result.singleFailure();

        assertTrue(failure instanceof IllegalStateException);
        assertTrue(failure.getMessage().contains("already in checkout"));

        assertNotNull(updatedOrder.getExpiresAt());
        assertTrue(updatedOrder.isInCheckout());

        verify(eventManagementService, times(1)).hold(eq(eventId), any());
    }
}