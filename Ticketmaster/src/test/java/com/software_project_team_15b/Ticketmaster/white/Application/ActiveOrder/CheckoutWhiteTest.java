package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.DTO.CheckoutStartedDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void startCheckoutForMemberShouldUseMemberBirthDate() {
        mockValidUser();
        when(auth.isMember(token)).thenReturn(true);

        Member member = mock(Member.class);
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        when(member.getBirthDate()).thenReturn(birthDate);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        ActiveOrder order = activeOrderWithSeats(seatId1);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
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

        when(eventDomainService.holdSeats(eq(eventId), eq(areaId), anyList(), eq(orderId)))
                .thenReturn(mock(HoldReceipt.class));

        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusMinutes(10);
        when(purchasingDomainService.startCheckout(order)).thenReturn(expiresAt);

        CheckoutStartedDTO result = service.startCheckoutForMember(token, orderId);

        assertEquals(expiresAt, result.expiresAt());
        verify(purchasingDomainService).buildPurchaseRequest(order, birthDate);
    }

    @Test
    void startCheckoutForMemberShouldRejectNonMember() {
        mockValidUser();
        when(auth.isMember(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.startCheckoutForMember(token, orderId)
        );

        verifyNoInteractions(memberRepository);
    }

    @Test
    void startCheckoutForGuestShouldRejectNonGuest() {
        mockValidUser();
        when(auth.isGuest(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.startCheckoutForGuest(token, orderId, LocalDate.of(2000, 1, 1))
        );
    }

    @Test
    void completeCheckoutForMemberShouldPayIssueFinalizeAndConfirm() {
        mockValidUser();
        when(auth.isMember(token)).thenReturn(true);

        Member member = mock(Member.class);
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        when(member.getBirthDate()).thenReturn(birthDate);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);
        int transactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(seatId1, "TICKET-1");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, "coupon"))
                .thenReturn(priceBreakdown);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(transactionId);

        when(ticketProvider.issueTickets(userId, eventId, areaId, Set.of(seatId1)))
                .thenReturn(issuedTicketIds);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);
        when(eventDomainService.confirm(eventId, orderId)).thenReturn(receipt);

        assertEquals(
                orderId,
                service.completeCheckoutForMember(token, orderId, "coupon", paymentDetails).orderId()
        );

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsInCheckout(order);
        verify(eventDomainService).getPrice(eventId, areaId, 1, userId, birthDate, "coupon");
        verify(paymentGateway).chargePayment(any(MoneyDTO.class), eq(paymentDetails));
        verify(ticketProvider).issueTickets(userId, eventId, areaId, Set.of(seatId1));
        verify(purchasingDomainService).finalizeCheckout(
                order,
                transactionId,
                priceBreakdown,
                issuedTicketIds
        );
        verify(eventDomainService).confirm(eventId, orderId);
    }

    @Test
    void completeCheckoutForMemberShouldRejectNonMember() {
        mockValidUser();
        when(auth.isMember(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.completeCheckoutForMember(
                        token,
                        orderId,
                        null,
                        mock(PaymentDetailsDTO.class)
                )
        );
    }

    @Test
    void completeCheckoutForMemberShouldRejectNullOrderId() {
        assertThrows(IllegalArgumentException.class, () ->
                service.completeCheckoutForMember(
                        token,
                        null,
                        null,
                        mock(PaymentDetailsDTO.class)
                )
        );
    }

    @Test
    void startCheckoutShouldNotReleaseHoldWhenNoHoldReceiptWasCreated() {
        mockValidGuest();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, seatsAvailability))
                .thenReturn(false);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(mock(PurchaseRequest.class));

        when(eventDomainService.holdSeats(eq(eventId), eq(areaId), anyList(), eq(orderId)))
                .thenReturn(null);

        when(purchasingDomainService.startCheckout(order))
                .thenThrow(new RuntimeException("start failed"));

        assertThrows(RuntimeException.class, () ->
                service.startCheckoutForGuest(token, orderId, birthDate)
        );

        verify(eventDomainService, never()).release(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldRejectNullOrderId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        null,
                        LocalDate.of(2000, 1, 1),
                        null,
                        mock(PaymentDetailsDTO.class)
                )
        );

        assertTrue(exception.getMessage().contains("Order ID cannot be null"));

        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(ticketProvider);
        verifyNoInteractions(eventDomainService);
    }

    @Test
    void completeCheckoutForGuestShouldRejectNullBirthDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        orderId,
                        null,
                        null,
                        mock(PaymentDetailsDTO.class)
                )
        );

        assertTrue(exception.getMessage().contains("Birth date cannot be null or in the future"));

        verifyNoInteractions(purchasingDomainService);
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(ticketProvider);
        verifyNoInteractions(eventDomainService);
    }

    @Test
    void startCheckoutForGuestShouldValidateOrderHoldSeatsAndStartCheckout() {
        mockValidGuest();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
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

        CheckoutStartedDTO view = service.startCheckoutForGuest(token, orderId, birthDate);

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
        mockValidGuest();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
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
        mockValidGuest();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
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
        mockValidGuest();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
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
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        int transactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(seatId1, "TICKET-1");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(transactionId);

        when(ticketProvider.issueTickets(userId, eventId, areaId, Set.of(seatId1)))
                .thenReturn(issuedTicketIds);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);

        when(eventDomainService.confirm(eventId, orderId))
                .thenReturn(receipt);

        service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails);

        verify(purchasingDomainService).getOwnedOrderForUpdate(userId, orderId);
        verify(purchasingDomainService).validateOrderIsInCheckout(order);

        verify(eventDomainService).getPrice(eventId, areaId, 1, userId, birthDate, null);

        verify(paymentGateway).chargePayment(any(MoneyDTO.class), eq(paymentDetails));
        verify(ticketProvider).issueTickets(userId, eventId, areaId, Set.of(seatId1));

        verify(purchasingDomainService).finalizeCheckout(
                order,
                transactionId,
                priceBreakdown,
                issuedTicketIds
        );

        verify(eventDomainService).confirm(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldThrowWhenPaymentFailsAndNotIssueTickets() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        doThrow(new FailedPaymentException("card declined"))
                .when(paymentGateway)
                .chargePayment(any(MoneyDTO.class), eq(paymentDetails));

        assertThrows(FailedPaymentException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails)
        );

        verify(paymentGateway).chargePayment(any(MoneyDTO.class), eq(paymentDetails));
        verify(ticketProvider, never()).issueTickets(any(), any(), any(), any());
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any(), any(), any());
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldRefundPaymentWhenTicketIssuanceFails() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        int transactionId = 12345;

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(transactionId);

        doThrow(new FailedToIssueTicketsException("issue failed"))
                .when(ticketProvider)
                .issueTickets(userId, eventId, areaId, Set.of(seatId1));

        assertThrows(FailedToIssueTicketsException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails)
        );

        verify(paymentGateway).refundPayment(transactionId);
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any(), any(), any());
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldCancelTicketsWhenFinalizeFailsAfterTicketsIssued() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        int transactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(seatId1, "TICKET-1");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(transactionId);

        when(ticketProvider.issueTickets(userId, eventId, areaId, Set.of(seatId1)))
                .thenReturn(issuedTicketIds);

        doThrow(new RuntimeException("finalize failed"))
                .when(purchasingDomainService)
                .finalizeCheckout(order, transactionId, priceBreakdown, issuedTicketIds);

        assertThrows(RuntimeException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails)
        );

        verify(paymentGateway).refundPayment(transactionId);
        verify(ticketProvider).cancelTicket("TICKET-1");
        verify(eventDomainService, never()).confirm(any(), any());
    }

    @Test
    void completeCheckoutForGuestShouldReleaseHoldWhenConfirmFailsAfterFinalize() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        int transactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(seatId1, "TICKET-1");

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(priceBreakdown);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(transactionId);

        when(ticketProvider.issueTickets(userId, eventId, areaId, Set.of(seatId1)))
                .thenReturn(issuedTicketIds);

        when(purchasingDomainService.finalizeCheckout(
                order,
                transactionId,
                priceBreakdown,
                issuedTicketIds
        )).thenReturn(order);

        when(eventDomainService.confirm(eventId, orderId))
                .thenThrow(new RuntimeException("confirm failed"));

        assertThrows(RuntimeException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails)
        );

        verify(paymentGateway).refundPayment(transactionId);
        verify(ticketProvider).cancelTicket("TICKET-1");
        verify(eventDomainService).release(eventId, orderId);
    }

    @Test
    void completeCheckoutForGuestShouldExpireOrderWhenCheckoutTimerExpired() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsInCheckout(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        assertThrows(TimeExpiredException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null, paymentDetails)
        );

        verify(eventDomainService).release(eventId, orderId);
        verify(purchasingDomainService).expireOrder(order);

        verify(eventDomainService, never()).getPrice(any(), any(), anyInt(), any(), any(), any());
        verify(paymentGateway, never()).chargePayment(any(), any());
        verify(ticketProvider, never()).issueTickets(any(), any(), any(), any());
        verify(purchasingDomainService, never()).finalizeCheckout(any(), any(), any(), any());
    }
}