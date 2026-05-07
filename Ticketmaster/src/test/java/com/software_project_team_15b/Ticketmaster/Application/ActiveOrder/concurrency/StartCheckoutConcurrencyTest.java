package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.concurrency;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.storage.mode=jpa",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:start-checkout-concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
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
        assertNotNull(updatedOrder.getExpiresAt());
        assertTrue(updatedOrder.isInCheckout());

        verify(eventManagementService, times(1)).hold(eq(eventId), any());
    }
}