package com.software_project_team_15b.Ticketmaster.black.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.CheckoutStartedView;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutBlackTest {

    @Mock
    private PurchasingDomainService purchasingDomainService;

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private IEventDomainService eventDomainService;

    @Mock
    private IQueueDomainService queueDomainService;

    @Mock
    private ILotteryDomainService lotteryDomainService;

    @Mock
    private IPaymentAPI paymentGateway;

    @Mock
    private ITicketSupplyAPI ticketProvider;

    @Mock
    private IAuth auth;

    private PurchasingService service;

    private String token;
    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;
    private UUID seatId1;

    @BeforeEach
    void setUp() {
        service = new PurchasingService(
                purchasingDomainService,
                memberRepository,
                eventDomainService,
                queueDomainService,
                lotteryDomainService,
                paymentGateway,
                ticketProvider,
                auth
        );

        token = "valid-token";
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
    }

    @Test
    void startCheckoutShouldSucceedWhenOrderIsValidSeatsAreAvailableAndPolicyPasses() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        PurchaseRequest purchaseRequest = mock(PurchaseRequest.class);
        HoldReceipt holdReceipt = mock(HoldReceipt.class);

        mockValidGuestOperation();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, Set.of(seatId1)))
                .thenReturn(availability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, availability))
                .thenReturn(false);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(purchaseRequest);

        when(eventDomainService.holdSeats(eventId, areaId, java.util.List.of(seatId1), orderId))
                .thenReturn(holdReceipt);

        when(purchasingDomainService.startCheckout(order))
                .thenReturn(expiresAt);

        CheckoutStartedView result = service.startCheckoutForGuest(token, orderId, birthDate);

        assertEquals(orderId, result.orderId());
        assertEquals(eventId, result.eventId());
        assertEquals(areaId, result.areaId());
        assertEquals(expiresAt, result.expiresAt());
    }

    @Test
    void startCheckoutShouldFailWhenSeatsBecameUnavailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(),
                false, Set.of(seatId1)
        );

        mockValidGuestOperation();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, Set.of(seatId1)))
                .thenReturn(availability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, availability))
                .thenReturn(true);

        assertThrows(OrderSeatsUnavailableException.class, () ->
                service.startCheckoutForGuest(token, orderId, birthDate)
        );
    }

    @Test
    void startCheckoutShouldFailWhenPurchasePolicyIsViolated() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        PurchaseRequest purchaseRequest = mock(PurchaseRequest.class);

        mockValidGuestOperation();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, Set.of(seatId1)))
                .thenReturn(availability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, availability))
                .thenReturn(false);

        when(purchasingDomainService.buildPurchaseRequest(order, birthDate))
                .thenReturn(purchaseRequest);

        doThrow(new PolicyViolationException("policy violated"))
                .when(eventDomainService)
                .validatePurchaseEligibility(eventId, purchaseRequest);

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () ->
                service.startCheckoutForGuest(token, orderId, birthDate)
        );

        assertTrue(exception.getMessage().contains("policy violated"));
    }

    @Test
    void completeCheckoutShouldSucceedWhenPaymentTicketsFinalizeAndConfirmSucceed() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PriceBreakdown price = priceBreakdown("100.00");
        Money total = money("100.00");

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> successfulTicketIssue = successfulResponse();

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);

        mockValidGuestOperation();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(price);

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(successfulTicketIssue);

        when(purchasingDomainService.finalizeCheckout(order, price))
                .thenReturn(order);

        when(eventDomainService.confirm(eventId, orderId))
                .thenReturn(receipt);

        assertDoesNotThrow(() ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );
    }

    @Test
    void completeCheckoutShouldFailWhenPaymentIsRejected() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PriceBreakdown price = priceBreakdown("100.00");
        Money total = money("100.00");

        Response<Boolean> failedPayment = failedResponse("card declined");

        mockValidGuestOperation();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(price);

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(failedPayment);

        FailedPaymentException exception = assertThrows(FailedPaymentException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        assertTrue(exception.getMessage().contains("card declined"));
    }

    @Test
    void completeCheckoutShouldFailWhenTicketIssuanceFails() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PriceBreakdown price = priceBreakdown("100.00");
        Money total = money("100.00");

        Response<Boolean> successfulPayment = successfulResponse();
        Response<Boolean> failedTicketIssue = failedResponse("issue failed");

        mockValidGuestOperation();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null))
                .thenReturn(price);

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(successfulPayment);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(failedTicketIssue);

        FailedToIssueTicketsException exception = assertThrows(FailedToIssueTicketsException.class, () ->
                service.completeCheckoutForGuest(token, orderId, birthDate, null)
        );

        assertTrue(exception.getMessage().contains("issue failed"));
    }

    private void mockValidGuestOperation() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        when(auth.isGuest(token)).thenReturn(false);
    }

    private void mockPurchaseAccess(boolean hasQueueAccess) {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);
    }

    private ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seats));
        return order;
    }

    private Money money(String amount) {
        return Money.of(amount, "ILS");
    }

    private PriceBreakdown priceBreakdown(String total) {
        Money basePrice = money(total);
        Money subtotal = money(total);
        Money discount = money("0.00");
        Money totalPrice = money(total);

        return new PriceBreakdown(basePrice, subtotal, discount, totalPrice);
    }

    @SuppressWarnings("unchecked")
    private Response<Boolean> successfulResponse() {
        Response<Boolean> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Response<Boolean> failedResponse(String errorMessage) {
        Response<Boolean> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getErrorMessage()).thenReturn(errorMessage);
        return response;
    }
}