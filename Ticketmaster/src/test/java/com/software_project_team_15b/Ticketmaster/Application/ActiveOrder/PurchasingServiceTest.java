package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchasingServiceTest {

    @Mock
    private IActiveOrderRepository activeOrderRepository;

    @Mock
    private IOrderHistoryRepository orderHistoryRepository;

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private EventManagementService eventManagementService;

    @Mock
    private QueueService queueService;

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
    private UUID seatId2;

    @BeforeEach
    void setUp() {
        service = new PurchasingService(
                activeOrderRepository,
                orderHistoryRepository,
                memberRepository,
                eventManagementService,
                queueService,
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
        seatId2 = UUID.randomUUID();
    }

    // ---------- createActiveOrder ----------

    @Test
    void createActiveOrderShouldSaveNewOrderWhenUserHasAccess() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        ArgumentCaptor<ActiveOrder> captor = ArgumentCaptor.forClass(ActiveOrder.class);

        UUID createdOrderId = service.createActiveOrder(token, eventId, areaId);

        verify(activeOrderRepository).saveAndFlush(captor.capture());

        ActiveOrder savedOrder = captor.getValue();

        assertNotNull(createdOrderId);
        assertEquals(createdOrderId, savedOrder.getOrderId());
        assertEquals(userId, savedOrder.getUserId());
        assertEquals(eventId, savedOrder.getEventId());
        assertEquals(areaId, savedOrder.getAreaId());
        assertEquals(ActiveOrderStatus.ACTIVE, savedOrder.getStatus());
        assertEquals(Boolean.TRUE, savedOrder.getActiveUniquenessKey());
        assertNull(savedOrder.getExpiresAt());
    }

    @Test
    void createActiveOrderShouldThrowWhenTokenIsInvalid() {
        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(auth, never()).extractUserId(any());
        verify(eventManagementService, never()).getEventAvailability(any());
        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenEventIdIsNull() {
        mockValidUser();

        assertThrows(IllegalArgumentException.class, () ->
                service.createActiveOrder(token, null, areaId)
        );

        verify(eventManagementService, never()).getEventAvailability(any());
        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenEventIsNotAvailable() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenAreaIdIsNull() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        assertThrows(IllegalArgumentException.class, () ->
                service.createActiveOrder(token, eventId, null)
        );

        verify(eventManagementService, never()).getAreaAvailability(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenAreaIsNotAvailable() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(activeOrderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenUserAlreadyHasActiveOrderForEvent() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(queueService, never()).hasAccess(any(), any());
        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldThrowWhenQueueAccessIsMissing() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        when(queueService.hasAccess(token, eventId)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        verify(activeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createActiveOrderShouldConvertDataIntegrityViolationToIllegalStateException() {
        mockValidUser();

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);

        when(activeOrderRepository.existsByUserIdAndEventIdAndStatus(
                userId,
                eventId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(false);

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        when(activeOrderRepository.saveAndFlush(any(ActiveOrder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active order"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("User already has an active order"));
    }

    // ---------- addSeatsToExistingOrder ----------

    @Test
    void addSeatsToExistingOrderShouldAddSeatsWhenAllRequestedSeatsAreAvailable() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(Map.of(
                        true, requestedSeats,
                        false, Set.of()
                ));

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        service.addSeatsToExistingOrder(token, cmd);

        assertEquals(requestedSeats, order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    @Test
    void addSeatsToExistingOrderShouldThrowAndNotSaveWhenSomeSeatsAreUnavailable() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(Map.of(
                        true, Set.of(seatId1),
                        false, Set.of(seatId2)
                ));

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        assertThrows(IllegalStateException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(order.getOrderSeats().isEmpty());
        verify(activeOrderRepository, never()).save(order);
    }

    // ---------- removeSeatsFromExistingOrder ----------

    @Test
    void removeSeatsFromExistingOrderShouldRemoveSeatsFromOrder() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId1, seatId2));

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        service.removeSeatsFromExistingOrder(token, cmd);

        assertEquals(Set.of(seatId2), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    // ---------- startCheckout ----------

    @Test
    void startCheckoutForGuestShouldHoldSeatsAndSetExpiration() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId1));

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

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

        LocalDate guestBirthDate = LocalDate.of(2000, 1, 1);

        CheckoutStartedView view =
                service.startCheckoutForGuest(token, orderId, guestBirthDate);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());
        assertEquals(areaId, view.areaId());
        assertNotNull(view.expiresAt());

        assertNotNull(order.getExpiresAt());
        verify(activeOrderRepository).save(order);
        verify(eventManagementService).hold(eq(eventId), any());
    }

    @Test
    void startCheckoutForGuestShouldRemoveUnavailableSeatsAndThrow() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId1, seatId2));

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, Set.of(seatId1, seatId2)))
                .thenReturn(Map.of(
                        true, Set.of(seatId1),
                        false, Set.of(seatId2)
                ));

        LocalDate guestBirthDate = LocalDate.of(2000, 1, 1);

        assertThrows(OrderSeatsUnavailableException.class, () ->
                service.startCheckoutForGuest(token, orderId, guestBirthDate)
        );

        assertEquals(Set.of(seatId1), order.getOrderSeats());

        verify(activeOrderRepository).save(order);
        verify(eventManagementService, never()).hold(any(), any());
    }

    // ---------- completeCheckout ----------

    @Test
    void completeCheckoutForGuestShouldPayIssueTicketsSaveHistoryAndConfirm() {
        mockValidUser();

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        Money basePrice = Money.of("100.00", "ILS");
        Money subtotal = Money.of("100.00", "ILS");
        Money discount = Money.of("0.00", "ILS");
        Money total = Money.of("100.00", "ILS");

        PriceBreakdown priceBreakdown =
                new PriceBreakdown(basePrice, subtotal, discount, total);

        when(eventManagementService.getPrice(eq(eventId), any()))
                .thenReturn(priceBreakdown);

        Response<Boolean> succResponse = successfulResponse();

        when(paymentGateway.chargePayment(token, total))
                .thenReturn(succResponse);

        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1)))
                .thenReturn(succResponse);

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

        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seatId1));
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        Money basePrice = Money.of("100.00", "ILS");
        Money subtotal = Money.of("100.00", "ILS");
        Money discount = Money.of("0.00", "ILS");
        Money total = Money.of("100.00", "ILS");

        PriceBreakdown priceBreakdown =
                new PriceBreakdown(basePrice, subtotal, discount, total);

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

    // ---------- cancel ----------

    @Test
    void cancelAllActiveOrdersOfCurrentUserShouldCancelAllActiveOrders() {
        mockValidUser();

        ActiveOrder order1 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);
        ActiveOrder order2 = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);

        when(activeOrderRepository.findByUserIdAndStatusForUpdate(
                userId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(java.util.List.of(order1, order2));

        service.cancelAllActiveOrdersOfCurrentUser(token);

        assertEquals(ActiveOrderStatus.CANCELED, order1.getStatus());
        assertEquals(ActiveOrderStatus.CANCELED, order2.getStatus());

        verify(activeOrderRepository).save(order1);
        verify(activeOrderRepository).save(order2);
    }

    private void mockValidUser() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
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