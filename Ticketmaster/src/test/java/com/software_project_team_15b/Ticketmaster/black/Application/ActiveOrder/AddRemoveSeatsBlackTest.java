package com.software_project_team_15b.Ticketmaster.black.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRemoveSeatsBlackTest {

    @Mock
    private PurchasingDomainService purchasingDomainService;

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private UserDomainService userDomainService;

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

    @Mock
    private INotifier notifier;

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

        token = "valid-token";
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();
    }

    @Test
    void addSeatsShouldSucceedWhenUserHasAccessAndSeatsAreAvailable() {
        ActiveOrder order = activeOrder();
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);
        Map<Boolean, Set<UUID>> availability = Map.of(
                true, requestedSeats,
                false, Set.of()
        );

        mockValidUser();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(availability);

        when(purchasingDomainService.requireRequestedSeatsAvailable(availability, requestedSeats))
                .thenReturn(requestedSeats);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        assertDoesNotThrow(() ->
                service.addSeatsToExistingOrder(token, cmd)
        );
    }

    @Test
    void addSeatsShouldFailWhenSomeSeatsAreUnavailable() {
        ActiveOrder order = activeOrder();
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        mockValidUser();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(availability);

        when(purchasingDomainService.requireRequestedSeatsAvailable(availability, requestedSeats))
                .thenThrow(new IllegalStateException("Some seats are not available"));

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("Some seats are not available"));
    }

    @Test
    void addSeatsShouldFailWhenUserDoesNotHavePurchaseAccess() {
        ActiveOrder order = activeOrder();
        Set<UUID> requestedSeats = Set.of(seatId1);

        LotteryEligibilityDTO eligibility = mockValidUserAndAccessObjects(false);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("User does not have access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats);

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
    }

    @Test
    void removeSeatsShouldSucceedWhenUserHasAccess() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        Set<UUID> seatsToRemove = Set.of(seatId1);

        mockValidUser();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, seatsToRemove);

        assertDoesNotThrow(() ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );
    }

    @Test
    void removeSeatsShouldFailWhenUserDoesNotHavePurchaseAccess() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        Set<UUID> seatsToRemove = Set.of(seatId1);

        LotteryEligibilityDTO eligibility = mockValidUserAndAccessObjects(false);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("User does not have access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, seatsToRemove);

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
    }

    private void mockValidUser() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
    }

    private void mockPurchaseAccess(boolean hasQueueAccess) {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);
    }

    private LotteryEligibilityDTO mockValidUserAndAccessObjects(boolean hasQueueAccess) {
        mockValidUser();

        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);

        return eligibility;
    }

    private ActiveOrder activeOrder() {
        return new ActiveOrder(orderId, userId, eventId, areaId);
    }

    private ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = activeOrder();
        order.addSeats(Set.of(seats));
        return order;
    }
}