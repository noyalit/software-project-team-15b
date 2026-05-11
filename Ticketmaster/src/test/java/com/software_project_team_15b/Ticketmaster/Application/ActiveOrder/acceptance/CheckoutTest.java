package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.CheckoutStartedView;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CheckoutTest extends PurchasingServiceTestBase {

    @Test
    void startCheckoutForGuestShouldHoldSeatsAndSetExpiration() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        when(eventManagementService.getSeatsAvailability(eventId, areaId, Set.of(seatId1)))
                .thenReturn(Map.of(
                        true, Set.of(seatId1),
                        false, Set.of()
                ));

        doNothing().when(eventManagementService)
                .validatePurchaseEligibility(eq(eventId), any());

        HoldReceipt holdReceipt = mock(HoldReceipt.class);

        when(eventManagementService.hold(eq(eventId), any()))
                .thenReturn(holdReceipt);

        CheckoutStartedView view = service.startCheckoutForGuest(
                token,
                orderId,
                LocalDate.of(2000, 1, 1)
        );

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());
        assertEquals(areaId, view.areaId());
        assertNotNull(view.expiresAt());

        assertNotNull(order.getExpiresAt());
        verify(activeOrderRepository).save(order);
        verify(eventManagementService).hold(eq(eventId), any());
    }

    @Test
    void startCheckoutShouldRejectWhenPurchasePolicyViolated() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        when(eventManagementService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(Map.of(
                        true, order.getOrderSeats(),
                        false, Set.of()
                ));

        doThrow(new PolicyViolationException("policy violated"))
                .when(eventManagementService)
                .validatePurchaseEligibility(eq(eventId), any());

        assertThrows(PolicyViolationException.class, () ->
                service.startCheckoutForGuest(token, orderId, LocalDate.of(2000, 1, 1))
        );

        assertEquals(ActiveOrderStatus.CANCELED, order.getStatus());
        verify(activeOrderRepository).save(order);
        verify(eventManagementService, never()).hold(any(), any());
    }

    @Test
    void startCheckoutForGuestShouldRemoveUnavailableSeatsAndThrow() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        when(eventManagementService.getSeatsAvailability(eventId, areaId, Set.of(seatId1, seatId2)))
                .thenReturn(Map.of(
                        true, Set.of(seatId1),
                        false, Set.of(seatId2)
                ));

        assertThrows(OrderSeatsUnavailableException.class, () ->
                service.startCheckoutForGuest(token, orderId, LocalDate.of(2000, 1, 1))
        );

        assertEquals(Set.of(seatId1), order.getOrderSeats());

        verify(activeOrderRepository).save(order);
        verify(eventManagementService, never()).hold(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldPayIssueTicketsSaveHistoryAndConfirm() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        mockOrderFoundForUpdate(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPaymentResponse = successfulResponse();
        Response<Boolean> successfulTicketIssueResponse = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPaymentResponse);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(successfulTicketIssueResponse);

        ConfirmationReceipt confirmationReceipt = mock(ConfirmationReceipt.class);
        when(confirmationReceipt.areaId()).thenReturn(areaId);
        when(confirmationReceipt.quantity()).thenReturn(1);

        when(eventManagementService.confirm(eventId, orderId))
                .thenReturn(confirmationReceipt);

        service.completeCheckoutForGuest(
                token,
                orderId,
                LocalDate.of(2000, 1, 1),
                null
        );

        assertEquals(ActiveOrderStatus.COMPLETED, order.getStatus());

        verify(activeOrderRepository, atLeastOnce()).save(order);
        verify(orderHistoryRepository).save(any(OrderHistory.class));
        verify(eventManagementService).confirm(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldThrowWhenPaymentFails() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        mockOrderFoundForUpdate(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown);

        Response<Boolean> failedPaymentResponse = failedResponse("card declined");

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(failedPaymentResponse);

        assertThrows(FailedPaymentException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        orderId,
                        LocalDate.of(2000, 1, 1),
                        null
                )
        );

        verify(ticketProvider, never()).issueTickets(any(), any(), any());
        verify(eventManagementService, never()).confirm(any(), any());
        verify(orderHistoryRepository, never()).save(any());
    }

    @Test
    void completeCheckoutForGuestShouldRefundWhenTicketIssuanceFails() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        mockOrderFoundForUpdate(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPaymentResponse = successfulResponse();
        Response<Boolean> failedTicketIssueResponse = failedResponse("issue failed");

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPaymentResponse);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(failedTicketIssueResponse);

        assertThrows(FailedToIssueTicketsException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        orderId,
                        LocalDate.of(2000, 1, 1),
                        null
                )
        );

        verify(paymentGateway).refundPayment(token, total);
        verify(eventManagementService, never()).confirm(any(), any());
        verify(orderHistoryRepository, never()).save(any());
    }

    @Test
    void completeCheckoutForGuestShouldReleaseSeatsWhenTimerExpiredDuringCheckout() {
        mockValidUser();

        ActiveOrder order = mockExpiredCheckoutOrder();

        assertThrows(TimeExpiredException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        orderId,
                        LocalDate.of(2000, 1, 1),
                        null
                )
        );

        verify(eventManagementService).release(eventId, orderId);
        verify(order).expire();
        verify(activeOrderRepository).save(order);

        verify(eventManagementService, never()).getPrice(any(), any());
        verify(paymentGateway, never()).chargePayment(any(), any());
        verify(ticketProvider, never()).issueTickets(any(), any(), any());
        verify(orderHistoryRepository, never()).save(any());
    }
}
