package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

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
    public void requireToken_nullOrBlank_throws() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireToken", new Class[]{String.class}, (Object) null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireToken", new Class[]{String.class}, "   "));
    }

    @Test
    public void requireValidUser_invalidToken_throws() throws Throwable {
        String token = "t";
        when(auth.isTokenValid(token)).thenReturn(false);

        Throwable ex = assertThrows(Throwable.class, () -> callPrivate("requireValidUser", new Class[]{String.class}, token));
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    public void getUserBirthDate_userNotFound_throws() throws Throwable {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findById(userId)).thenReturn(Optional.empty());

        Throwable ex = assertThrows(Throwable.class, () -> callPrivate("getUserBirthDate", new Class[]{UUID.class}, userId));
        assertTrue(ex instanceof IllegalStateException);
    }

    @Test
    public void holdSeatsForActiveOrder_nullOrEmpty_throws() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> callPrivate("holdSeatsForActiveOrder", new Class[]{ActiveOrder.class}, (Object) null));

        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        // no seats
        assertThrows(IllegalArgumentException.class, () -> callPrivate("holdSeatsForActiveOrder", new Class[]{ActiveOrder.class}, ao));
    }

    @Test
    public void syncOrderSeatsAvailability_nullOrEmpty_behaviour() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> callPrivate("syncOrderSeatsAvailability", new Class[]{ActiveOrder.class}, (Object) null));

        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        // empty seats should return false
        Object res = callPrivate("syncOrderSeatsAvailability", new Class[]{ActiveOrder.class}, ao);
        assertEquals(Boolean.FALSE, res);
    }

    @Test
    public void requireSeatIds_nullEmptyOrContainsNull_throws() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireSeatIds", new Class[]{Set.class}, (Object) null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireSeatIds", new Class[]{Set.class}, new HashSet<>()));
        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireSeatIds", new Class[]{Set.class}, withNull));
    }

    @Test
    public void requireRemoveOrAddSeatsFromActiveOrderCommand_nullOrMissingOrderId_throws() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireRemoveOrAddSeatsFromActiveOrderCommand", new Class[]{RemoveOrAddSeatsFromActiveOrderCommand.class}, (Object) null));

        RemoveOrAddSeatsFromActiveOrderCommand cmd = new RemoveOrAddSeatsFromActiveOrderCommand(null, Set.of(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireRemoveOrAddSeatsFromActiveOrderCommand", new Class[]{RemoveOrAddSeatsFromActiveOrderCommand.class}, cmd));
    }

    @Test
    public void issueTickets_failure_throws() throws Throwable {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ao.addSeats(Set.of(UUID.randomUUID()));

        @SuppressWarnings("unchecked")
        Response<Boolean> failed = mock(Response.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getErrorMessage()).thenReturn("err");
        when(ticketProvider.issueTickets(any(), any(), any())).thenReturn(failed);

        Throwable ex = assertThrows(Throwable.class, () -> callPrivate("issueTickets", new Class[]{ActiveOrder.class}, ao));
        assertTrue(ex.getClass().getSimpleName().contains("FailedToIssueTicketsException"));
    }

    @Test
    public void pay_failure_throws() throws Throwable {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Money money = mock(Money.class);

        @SuppressWarnings("unchecked")
        Response<Boolean> failed = mock(Response.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getErrorMessage()).thenReturn("err");
        when(paymentGateway.chargePayment(anyString(), any())).thenReturn(failed);

        Throwable ex = assertThrows(Throwable.class, () -> callPrivate("pay", new Class[]{ActiveOrder.class, String.class, Money.class}, ao, "t", money));
        assertTrue(ex.getClass().getSimpleName().contains("FailedPaymentException"));
    }

    @Test
    public void compensateCheckoutFailure_refundAndRevokeAndReleaseHold() throws Throwable {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ao.addSeats(Set.of(UUID.randomUUID()));

        PriceBreakdown pb = mock(PriceBreakdown.class);
        Money money = mock(Money.class);
        when(pb.total()).thenReturn(money);

        // invoke compensateCheckoutFailure with paymentSucceeded=true, ticketsIssued=true, finalizeDone=true, confirmed=false
        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class}, "token", ao, pb, true, true, true, false);

        verify(paymentGateway).refundPayment("token", money);
        verify(ticketProvider).cancelTickets(eq(ao.getEventId()), eq(ao.getAreaId()), eq(ao.getOrderSeats()));
        verify(eventDomainService).release(eq(ao.getEventId()), eq(ao.getOrderId()));
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
                service.completeCheckoutForGuest(token, UUID.randomUUID(), LocalDate.of(2000, 1, 1), null)
        );
    }

    @Test
    public void cancelAllAndGuestLogout_errorAndListenerBranches() {
        String token = "token";
        UUID userId = UUID.randomUUID();
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId)).thenThrow(new RuntimeException("cancel failed"));

        assertThrows(RuntimeException.class, () -> service.cancelAllActiveOrdersOfCurrentUser(token));

        reset(purchasingDomainService);
        when(purchasingDomainService.getActiveOrdersOfUserForUpdate(userId)).thenReturn(List.of());
        service.handleGuestLoggedOut(new GuestLoggedOutEvent(token));
        verify(purchasingDomainService).getActiveOrdersOfUserForUpdate(userId);
    }

    @Test
    public void privateValidationAndCompensationBranches() throws Throwable {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        order.addSeats(Set.of(UUID.randomUUID()));

        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", null, null, false, false, false, false);
        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", order, null, false, false, false, true);

        assertThrows(IllegalArgumentException.class, () -> callPrivate("issueTickets", new Class[]{ActiveOrder.class}, (Object) null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("pay", new Class[]{ActiveOrder.class, String.class, Money.class}, null, "token", mock(Money.class)));
        callPrivate("releaseHold", new Class[]{ActiveOrder.class}, (Object) null);

        when(purchasingDomainService.shouldReleaseHoldBeforeCancel(order)).thenReturn(false);
        callPrivate("releaseHoldIfNeeded", new Class[]{ActiveOrder.class}, order);
        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order)).thenReturn(false);
        callPrivate("releaseSeatsIfExpiredAndStillActive", new Class[]{ActiveOrder.class}, order);
        callPrivate("releaseSeatsIfExpiredAndStillActive", new Class[]{ActiveOrder.class}, (Object) null);

        assertThrows(IllegalArgumentException.class, () -> callPrivate("getUserBirthDate", new Class[]{UUID.class}, (Object) null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("getPriceBreakdown", new Class[]{ActiveOrder.class, String.class, LocalDate.class}, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireAccessForPurchase", new Class[]{String.class, UUID.class, UUID.class}, null, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireAccessForPurchase", new Class[]{String.class, UUID.class, UUID.class}, "token", null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireAccessForPurchase", new Class[]{String.class, UUID.class, UUID.class}, "token", UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requirePurchaseEligibility", new Class[]{ActiveOrder.class, LocalDate.class}, null, LocalDate.of(2000, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireBirthDate", new Class[]{LocalDate.class}, LocalDate.now().plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireCreateActiveOrderArguments", new Class[]{UUID.class, UUID.class}, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> callPrivate("requireEventId", new Class[]{UUID.class}, (Object) null));
    }

    @Test
    public void compensateCheckoutFailure_releaseConditionFalseBranches() throws Throwable {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        order.addSeats(Set.of(UUID.randomUUID()));

        PriceBreakdown pricing = mock(PriceBreakdown.class);
        when(pricing.total()).thenReturn(mock(Money.class));

        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", order, null, false, false, false, false);
        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", order, pricing, true, false, false, false);
        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", order, pricing, true, true, false, false);
        callPrivate("compensateCheckoutFailure", new Class[]{String.class, ActiveOrder.class, PriceBreakdown.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "token", order, pricing, true, true, true, true);

        verify(eventDomainService, never()).release(order.getEventId(), order.getOrderId());
    }
}
