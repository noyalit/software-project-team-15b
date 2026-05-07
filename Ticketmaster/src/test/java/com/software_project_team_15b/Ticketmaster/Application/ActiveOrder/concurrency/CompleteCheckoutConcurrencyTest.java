package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.concurrency;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompleteCheckoutConcurrencyTest extends ConcurrencyTestSupport {

    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;
    private UUID seatId;

    private Money total;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        mockValidUser(userId);

        total = Money.of("100.00", "ILS");

        PriceBreakdown priceBreakdown = new PriceBreakdown(
                Money.of("100.00", "ILS"),
                Money.of("100.00", "ILS"),
                Money.of("0.00", "ILS"),
                total
        );

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown);

        var success = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(success);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId)))
                .thenReturn(success);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);

        when(eventManagementService.confirm(eventId, orderId))
                .thenReturn(receipt);

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        activeOrderRepository.save(order);
    }

    @Test
    void concurrentCompleteCheckoutShouldChargeIssueTicketsConfirmAndSaveHistoryOnlyOnce() throws Exception {
        ConcurrencyResult result = runTwoThreads(() ->
                purchasingService.completeCheckoutForGuest(
                        token,
                        orderId,
                        LocalDate.of(2000, 1, 1),
                        null
                )
        );

        ActiveOrder completedOrder =
                activeOrderRepository.findById(orderId).orElseThrow();

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(ActiveOrderStatus.COMPLETED, completedOrder.getStatus());

        verify(paymentGateway, times(1)).chargePayment(token, total);
        verify(ticketProvider, times(1)).issueTickets(eventId, areaId, Set.of(seatId));
        verify(eventManagementService, times(1)).confirm(eventId, orderId);
        verify(orderHistoryRepository, times(1)).save(any(OrderHistory.class));
    }
}