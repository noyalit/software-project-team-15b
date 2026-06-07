package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

public class PurchasingServiceWhiteTest {

    PurchasingService service;
    PurchasingDomainService purchasingDomainService;
    IMemberRepository memberRepository;
    UserDomainService userDomainService;
    IEventDomainService eventDomainService;
    IQueueDomainService queueDomainService;
    ILotteryDomainService lotteryDomainService;
    IPaymentAPI paymentGateway;
    ITicketSupplyAPI ticketProvider;
    IAuth auth;
    INotifier notifier;

    @BeforeEach
    public void setUp() {
        purchasingDomainService = mock(PurchasingDomainService.class);
        memberRepository = mock(IMemberRepository.class);
        userDomainService = mock(UserDomainService.class);
        eventDomainService = mock(IEventDomainService.class);
        queueDomainService = mock(IQueueDomainService.class);
        lotteryDomainService = mock(ILotteryDomainService.class);
        paymentGateway = mock(IPaymentAPI.class);
        ticketProvider = mock(ITicketSupplyAPI.class);
        auth = mock(IAuth.class);
        notifier = mock(INotifier.class);

        service = new PurchasingService(
                purchasingDomainService,
                memberRepository,
                userDomainService,
                eventDomainService,
                queueDomainService,
                lotteryDomainService,
                paymentGateway,
                ticketProvider,
                auth,
                notifier
        );
    }

    private Object callPrivate(String name, Class<?>[] paramTypes, Object... args) throws Throwable {
        Method m = PurchasingService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);

        try {
            return m.invoke(service, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void requireToken_nullOrBlank_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireToken", new Class[]{String.class}, (Object) null));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireToken", new Class[]{String.class}, "   "));
    }

    @Test
    public void requireValidUser_invalidToken_throws() {
        String token = "t";
        when(auth.isTokenValid(token)).thenReturn(false);

        Throwable ex = assertThrows(Throwable.class,
                () -> callPrivate("requireValidUser", new Class[]{String.class}, token));

        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    public void getUserBirthDate_userNotFound_throws() {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findById(userId)).thenReturn(Optional.empty());

        Throwable ex = assertThrows(Throwable.class,
                () -> callPrivate("getUserBirthDate", new Class[]{UUID.class}, userId));

        assertTrue(ex instanceof IllegalStateException);
    }

    @Test
    public void holdSeatsForActiveOrder_nullOrEmpty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("holdSeatsForActiveOrder", new Class[]{ActiveOrder.class}, (Object) null));

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("holdSeatsForActiveOrder", new Class[]{ActiveOrder.class}, activeOrder));
    }

    @Test
    public void syncOrderSeatsAvailability_nullOrEmpty_behaviour() throws Throwable {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("syncOrderSeatsAvailability", new Class[]{ActiveOrder.class}, (Object) null));

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Object result = callPrivate("syncOrderSeatsAvailability", new Class[]{ActiveOrder.class}, activeOrder);

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void requireSeatIds_nullEmptyOrContainsNull_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireSeatIds", new Class[]{Set.class}, (Object) null));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireSeatIds", new Class[]{Set.class}, new HashSet<>()));

        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireSeatIds", new Class[]{Set.class}, withNull));
    }

    @Test
    public void requireRemoveOrAddSeatsFromActiveOrderCommand_nullOrMissingOrderId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireRemoveOrAddSeatsFromActiveOrderCommand",
                        new Class[]{RemoveOrAddSeatsFromActiveOrderCommand.class},
                        (Object) null
                ));

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(null, Set.of(UUID.randomUUID()));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireRemoveOrAddSeatsFromActiveOrderCommand",
                        new Class[]{RemoveOrAddSeatsFromActiveOrderCommand.class},
                        cmd
                ));
    }

    @Test
    public void issueTickets_nullActiveOrder_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("issueTickets", new Class[]{ActiveOrder.class}, (Object) null));
    }

    @Test
    public void issueTickets_validStandingOrder_returnsIssuedTicketIdFromProvider() throws Throwable {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                userId,
                eventId,
                areaId
        );
        activeOrder.addSeats(Set.of(seatId));

        String areaName = "area";
        String issuedTicketId = "TICKET-1";

        when(eventDomainService.getAreaName(eventId, areaId))
                .thenReturn(areaName);

        when(eventDomainService.isStandingArea(eventId, areaId))
                .thenReturn(true);

        when(ticketProvider.issueStandingTicket(userId, eventId, areaName, Set.of(seatId)))
                .thenReturn(issuedTicketId);

        Object result = callPrivate("issueTickets", new Class[]{ActiveOrder.class}, activeOrder);

        assertEquals(issuedTicketId, result);

        verify(eventDomainService).getAreaName(eventId, areaId);
        verify(eventDomainService).isStandingArea(eventId, areaId);
        verify(ticketProvider).issueStandingTicket(userId, eventId, areaName, Set.of(seatId));
        verify(ticketProvider, never()).issueSeatingTicket(any(), any(), any(), anyList());
    }

    @Test
    public void issueTickets_validSeatingOrder_returnsIssuedTicketIdFromProvider() throws Throwable {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                userId,
                eventId,
                areaId
        );
        activeOrder.addSeats(Set.of(seatId));

        String areaName = "VIP Balcony";
        String issuedTicketId = "TICKET-1";

        EventDTO.SeatView seatView = new EventDTO.SeatView(
                seatId,
                "A",
                "1",
                "AVAILABLE"
        );

        SeatTicketRequestDTO seatTicket = SeatTicketRequestDTO.fromSeatView(seatView);
        List<SeatTicketRequestDTO> seatTickets = List.of(seatTicket);

        when(eventDomainService.getAreaName(eventId, areaId))
                .thenReturn(areaName);

        when(eventDomainService.isStandingArea(eventId, areaId))
                .thenReturn(false);

        when(eventDomainService.areaSeats(eventId, areaId))
                .thenReturn(List.of(seatView));

        when(ticketProvider.issueSeatingTicket(userId, eventId, areaName, seatTickets))
                .thenReturn(issuedTicketId);

        Object result = callPrivate("issueTickets", new Class[]{ActiveOrder.class}, activeOrder);

        assertEquals(issuedTicketId, result);

        verify(eventDomainService).getAreaName(eventId, areaId);
        verify(eventDomainService).isStandingArea(eventId, areaId);
        verify(eventDomainService).areaSeats(eventId, areaId);
        verify(ticketProvider).issueSeatingTicket(userId, eventId, areaName, seatTickets);
        verify(ticketProvider, never()).issueStandingTicket(any(), any(), any(), anySet());
    }

    @Test
    public void issueTickets_validSeatingOrder_filtersOnlySeatsInOrder() throws Throwable {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID orderedSeatId = UUID.randomUUID();
        UUID otherSeatId = UUID.randomUUID();

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                userId,
                eventId,
                areaId
        );
        activeOrder.addSeats(Set.of(orderedSeatId));

        String areaName = "VIP Balcony";
        String issuedTicketId = "TICKET-1";

        EventDTO.SeatView orderedSeatView = new EventDTO.SeatView(
                orderedSeatId,
                "A",
                "1",
                "AVAILABLE"
        );

        EventDTO.SeatView otherSeatView = new EventDTO.SeatView(
                otherSeatId,
                "A",
                "2",
                "AVAILABLE"
        );

        SeatTicketRequestDTO expectedSeatTicket = SeatTicketRequestDTO.fromSeatView(orderedSeatView);
        List<SeatTicketRequestDTO> expectedSeatTickets = List.of(expectedSeatTicket);

        when(eventDomainService.getAreaName(eventId, areaId))
                .thenReturn(areaName);

        when(eventDomainService.isStandingArea(eventId, areaId))
                .thenReturn(false);

        when(eventDomainService.areaSeats(eventId, areaId))
                .thenReturn(List.of(orderedSeatView, otherSeatView));

        when(ticketProvider.issueSeatingTicket(userId, eventId, areaName, expectedSeatTickets))
                .thenReturn(issuedTicketId);

        Object result = callPrivate("issueTickets", new Class[]{ActiveOrder.class}, activeOrder);

        assertEquals(issuedTicketId, result);

        verify(ticketProvider).issueSeatingTicket(userId, eventId, areaName, expectedSeatTickets);
        verify(ticketProvider, never()).issueStandingTicket(any(), any(), any(), anySet());
    }

    @Test
    public void pay_nullArguments_throw() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Money amount = Money.of("100.00", "ILS");
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "pay",
                        new Class[]{ActiveOrder.class, Money.class, PaymentDetailsDTO.class},
                        null,
                        amount,
                        paymentDetails
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "pay",
                        new Class[]{ActiveOrder.class, Money.class, PaymentDetailsDTO.class},
                        activeOrder,
                        null,
                        paymentDetails
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "pay",
                        new Class[]{ActiveOrder.class, Money.class, PaymentDetailsDTO.class},
                        activeOrder,
                        amount,
                        null
                ));
    }

    @Test
    public void pay_validArguments_returnsTransactionIdFromGateway() throws Throwable {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Money amount = Money.of("100.00", "ILS");
        PaymentDetailsDTO paymentDetails = mock(PaymentDetailsDTO.class);

        when(paymentGateway.chargePayment(any(MoneyDTO.class), eq(paymentDetails)))
                .thenReturn(12345);

        Object result = callPrivate(
                "pay",
                new Class[]{ActiveOrder.class, Money.class, PaymentDetailsDTO.class},
                activeOrder,
                amount,
                paymentDetails
        );

        assertEquals(12345, result);
        verify(paymentGateway).chargePayment(any(MoneyDTO.class), eq(paymentDetails));
    }

    @Test
    public void compensateCheckoutFailure_refundAndRevokeAndReleaseHold() throws Throwable {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        activeOrder.addSeats(Set.of(UUID.randomUUID()));

        Integer transactionId = 12345;
        String issuedTicketId = "TICKET-1";

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                activeOrder,
                transactionId,
                issuedTicketId,
                true,
                true,
                true,
                false
        );

        verify(paymentGateway).refundPayment(transactionId);
        verify(ticketProvider).cancelTicket(issuedTicketId);
        verify(eventDomainService).release(activeOrder.getEventId(), activeOrder.getOrderId());
    }

    @Test
    public void compensateCheckoutFailure_doesNotCancelTicketWhenTicketWasNotIssued() throws Throwable {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Integer transactionId = 12345;

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                activeOrder,
                transactionId,
                null,
                true,
                false,
                false,
                false
        );

        verify(paymentGateway).refundPayment(transactionId);
        verify(ticketProvider, never()).cancelTicket(anyString());
        verify(eventDomainService, never()).release(any(), any());
    }

    @Test
    public void requestAccessToCreateActiveOrder_invalidToken_goesThroughCatch() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                service.requestAccessToCreateActiveOrder("bad", UUID.randomUUID())
        );

        verify(queueDomainService, never()).requestAccess(any(), any());
    }

    @Test
    public void completeCheckoutForGuest_rejectsNonGuest() {
        String token = "member-token";
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(UUID.randomUUID());
        when(auth.isGuest(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.completeCheckoutForGuest(
                        token,
                        UUID.randomUUID(),
                        LocalDate.of(2000, 1, 1),
                        null,
                        mock(PaymentDetailsDTO.class)
                )
        );
    }

    @Test
    public void cancelAllAndGuestLogout_errorAndListenerBranches() {
        String token = "token";
        UUID userId = UUID.randomUUID();

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId))
                .thenThrow(new RuntimeException("cancel failed"));

        assertThrows(RuntimeException.class, () ->
                service.cancelAllActiveOrdersOfCurrentUser(token)
        );

        reset(purchasingDomainService);

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId))
                .thenReturn(List.of());

        service.handleGuestLoggedOut(new GuestLoggedOutEvent(token));

        verify(purchasingDomainService).getActiveOrdersOfUserForUpdate(userId);
    }

    @Test
    public void privateValidationAndCompensationBranches() throws Throwable {
        ActiveOrder order = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        order.addSeats(Set.of(UUID.randomUUID()));

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                null,
                null,
                null,
                false,
                false,
                false,
                false
        );

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                order,
                null,
                null,
                false,
                false,
                false,
                true
        );

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("issueTickets", new Class[]{ActiveOrder.class}, (Object) null));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "pay",
                        new Class[]{ActiveOrder.class, Money.class, PaymentDetailsDTO.class},
                        null,
                        Money.of("100.00", "ILS"),
                        mock(PaymentDetailsDTO.class)
                ));

        callPrivate("releaseHold", new Class[]{ActiveOrder.class}, (Object) null);

        when(purchasingDomainService.shouldReleaseHoldBeforeCancel(order)).thenReturn(false);
        callPrivate("releaseHoldIfNeeded", new Class[]{ActiveOrder.class}, order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order)).thenReturn(false);
        callPrivate("releaseSeatsIfExpiredAndStillActive", new Class[]{ActiveOrder.class}, order);
        callPrivate("releaseSeatsIfExpiredAndStillActive", new Class[]{ActiveOrder.class}, (Object) null);

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("getUserBirthDate", new Class[]{UUID.class}, (Object) null));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "getPriceBreakdown",
                        new Class[]{ActiveOrder.class, String.class, LocalDate.class},
                        null,
                        null,
                        null
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireAccessForPurchase",
                        new Class[]{String.class, UUID.class, UUID.class},
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireAccessForPurchase",
                        new Class[]{String.class, UUID.class, UUID.class},
                        "token",
                        null,
                        UUID.randomUUID()
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireAccessForPurchase",
                        new Class[]{String.class, UUID.class, UUID.class},
                        "token",
                        UUID.randomUUID(),
                        null
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requirePurchaseEligibility",
                        new Class[]{ActiveOrder.class, LocalDate.class},
                        null,
                        LocalDate.of(2000, 1, 1)
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireBirthDate",
                        new Class[]{LocalDate.class},
                        LocalDate.now().plusDays(1)
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate(
                        "requireCreateActiveOrderArguments",
                        new Class[]{UUID.class, UUID.class},
                        UUID.randomUUID(),
                        null
                ));

        assertThrows(IllegalArgumentException.class,
                () -> callPrivate("requireEventId", new Class[]{UUID.class}, (Object) null));
    }

    @Test
    public void compensateCheckoutFailure_releaseConditionFalseBranches() throws Throwable {
        ActiveOrder order = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        order.addSeats(Set.of(UUID.randomUUID()));

        Integer transactionId = 12345;
        String issuedTicketId = "TICKET-1";

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                order,
                null,
                null,
                false,
                false,
                false,
                false
        );

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                order,
                transactionId,
                null,
                true,
                false,
                false,
                false
        );

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                order,
                transactionId,
                issuedTicketId,
                true,
                true,
                false,
                false
        );

        callPrivate(
                "compensateCheckoutFailure",
                new Class[]{
                        ActiveOrder.class,
                        Integer.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                order,
                transactionId,
                issuedTicketId,
                true,
                true,
                true,
                true
        );

        verify(eventDomainService, never()).release(order.getEventId(), order.getOrderId());
    }
}