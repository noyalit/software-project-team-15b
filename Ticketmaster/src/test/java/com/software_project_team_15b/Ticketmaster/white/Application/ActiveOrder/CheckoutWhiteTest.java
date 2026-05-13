package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.CheckoutStartedView;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CheckoutWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void startCheckoutForGuestShouldValidateOrderHoldSeatsAndStartCheckout() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<java.util.UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, seatsAvailability))
                .thenReturn(false);

        PurchaseRequest purchaseRequest = mock(PurchaseRequest.class);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(purchaseRequest);

        HoldReceipt holdReceipt = mock(HoldReceipt.class);

        when(eventDomainService.holdSeats(
                eventId,
                areaId,
                List.copyOf(order.getOrderSeats()),
                orderId
        )).thenReturn(holdReceipt);

        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusMinutes(10);

        when(purchasingDomainService.startCheckout(order))
                .thenReturn(expiresAt);

        CheckoutStartedView view = service.startCheckoutForGuest(token, orderId, birthDate);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());
        assertEquals(areaId, view.areaId());
        assertEquals(expiresAt, view.expiresAt());

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsModifiable(order);
        verify(purchasingDomainService).requirePurchaseAccess(any(), any(), any(), eq(true));

        verify(eventDomainService).getSeatsAvailability(eventId, areaId, order.getOrderSeats());
        verify(purchasingDomainService).syncOrderSeatsAvailability(order, seatsAvailability);

        verify(purchasingDomainService).buildPurchaseRequest(order, birthDate);
        verify(eventDomainService).validatePurchaseEligibility(eventId, purchaseRequest);

        verify(eventDomainService).holdSeats(
                eventId,
                areaId,
                List.copyOf(order.getOrderSeats()),
                orderId
        );

        verify(purchasingDomainService).startCheckout(order);
    }

    @Test
    void startCheckoutForGuestShouldThrowWhenSeatsBecameUnavailable() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<java.util.UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, seatsAvailability))
                .thenReturn(true);

        assertThrows(OrderSeatsUnavailableException.class, () ->
                service.startCheckoutForGuest(token, orderId, LocalDate.of(2000, 1, 1))
        );

        verify(purchasingDomainService).syncOrderSeatsAvailability(order, seatsAvailability);
        verify(eventDomainService, never()).validatePurchaseEligibility(any(), any());
        verify(eventDomainService, never()).holdSeats(any(), any(), any(), any());
        verify(purchasingDomainService, never()).startCheckout(any());
    }

    @Test
    void startCheckoutForGuestShouldCancelOrderWhenPurchasePolicyIsViolated() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<java.util.UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, seatsAvailability))
                .thenReturn(false);

        PurchaseRequest purchaseRequest = mock(PurchaseRequest.class);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(purchaseRequest);

        doThrow(new PolicyViolationException("policy violated"))
                .when(eventDomainService)
                .validatePurchaseEligibility(eventId, purchaseRequest);

        assertThrows(PolicyViolationException.class, () ->
                service.startCheckoutForGuest(token, orderId, birthDate)
        );

        verify(purchasingDomainService).buildPurchaseRequest(order, birthDate);
        verify(eventDomainService).validatePurchaseEligibility(eventId, purchaseRequest);
        verify(purchasingDomainService).cancelOrder(order);

        verify(eventDomainService, never()).holdSeats(any(), any(), any(), any());
        verify(purchasingDomainService, never()).startCheckout(any());
    }

    @Test
    void startCheckoutForGuestShouldReleaseHoldWhenStartCheckoutFailsAfterHoldWasCreated() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<java.util.UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, seatsAvailability))
                .thenReturn(false);

        PurchaseRequest purchaseRequest = mock(PurchaseRequest.class);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(purchaseRequest);

        HoldReceipt holdReceipt = mock(HoldReceipt.class);

        when(eventDomainService.holdSeats(any(), any(), any(), any()))
                .thenReturn(holdReceipt);

        when(purchasingDomainService.startCheckout(order))
                .thenThrow(new RuntimeException("failed to start checkout"));

        assertThrows(RuntimeException.class, () ->
                service.startCheckoutForGuest(token, orderId, birthDate)
        );

        verify(eventDomainService).release(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldPayIssueTicketsFinalizeAndConfirm() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> successfulTicketIssue = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(successfulTicketIssue);

        when(purchasingDomainService.finalizeCheckout(order, priceBreakdown))
                .thenReturn(order);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);

        when(eventDomainService.confirm(eventId, orderId))
                .thenReturn(receipt);

        service.completeCheckoutForGuest(token, orderId, birthDate, null);

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsInCheckout(order);

        verify(eventDomainService).getPrice(eventId, areaId, 1, userId, birthDate, null);

        verify(paymentGateway).chargePayment(token, total);
        verify(ticketProvider).issueTickets(eventId, areaId, Set.of(seatId1));

        verify(purchasingDomainService).finalizeCheckout(order, priceBreakdown);
        verify(eventDomainService).confirm(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldThrowWhenPaymentFailsAndNotIssueTickets() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        Response<Boolean> failedPayment = failedResponse("card declined");

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(failedPayment);

        assertThrows(FailedPaymentException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        verify(paymentGateway).chargePayment(token, total);
        verify(ticketProvider, never()).issueTickets(any(), any(), any());
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any());
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldRefundPaymentWhenTicketIssuanceFails() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> failedTicketIssue = failedResponse("issue failed");

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(failedTicketIssue);

        assertThrows(FailedToIssueTicketsException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        verify(paymentGateway).refundPayment(token, total);
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any());
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldCancelTicketsWhenFinalizeFailsAfterTicketsIssued() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> successfulTickets = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(successfulTickets);

        when(purchasingDomainService.finalizeCheckout(order, priceBreakdown))
                .thenThrow(new RuntimeException("finalize failed"));

        assertThrows(RuntimeException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        verify(paymentGateway).refundPayment(token, total);
        verify(ticketProvider).cancelTickets(eventId, areaId, Set.of(seatId1));
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldReleaseHoldWhenConfirmFailsAfterFinalize() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> successfulTickets = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(successfulTickets);

        when(purchasingDomainService.finalizeCheckout(order, priceBreakdown))
                .thenReturn(order);

        when(eventDomainService.confirm(eventId, orderId))
                .thenThrow(new RuntimeException("confirm failed"));

        assertThrows(RuntimeException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        verify(paymentGateway).refundPayment(token, total);
        verify(ticketProvider).cancelTickets(eventId, areaId, Set.of(seatId1));
        verify(eventDomainService).release(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldExpireOrderWhenCheckoutTimerExpired() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsInCheckout(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        assertThrows(TimeExpiredException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);

        verify(eventDomainService, never()).getPrice(any(), any(), anyInt(), any(), any(), any());
        verify(paymentGateway, never()).chargePayment(any(), any());
        verify(ticketProvider, never()).issueTickets(any(), any(), any());
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any());
    }
}