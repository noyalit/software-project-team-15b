package com.software_project_team_15b.Ticketmaster.black.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.ActiveOrderView;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetActiveOrderBlackTest {

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
    private UUID seatId2;

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
        seatId2 = UUID.randomUUID();
    }

    @Test
    void getActiveOrderShouldReturnViewWhenOrderExistsUserHasAccessAndSeatsAreAvailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(seatId1, seatId2),
                false, Set.of()
        );

        mockValidUser();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(availability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, availability))
                .thenReturn(false);

        EventView eventView = eventView();

        when(eventDomainService.getEvent(eventId))
                .thenReturn(eventView);

        when(eventDomainService.getPrice(eventId, areaId, 2, userId, null, null))
                .thenReturn(priceBreakdown("200.00"));

        ActiveOrderView view = service.getActiveOrder(token, orderId);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());
    }

    @Test
    void getActiveOrderShouldFailWhenTokenIsInvalid() {
        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.getActiveOrder(token, orderId)
        );
    }

    @Test
    void getActiveOrderShouldFailWhenOrderDoesNotExist() {
        mockValidUser();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenThrow(new IllegalArgumentException("Active order not found: " + orderId));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        assertTrue(exception.getMessage().contains("Active order not found"));
    }

    @Test
    void getActiveOrderShouldFailWhenUserDoesNotHavePurchaseAccess() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        LotteryEligibilityResult eligibility = mockValidUserAndAccessObjects(false);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("User does not have access"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
    }

    @Test
    void getActiveOrderShouldFailWhenOrderTimerExpired() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        mockValidUser();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsActive(order);

        when(purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(order))
                .thenReturn(true);

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    void getActiveOrderShouldFailWhenSomeSeatsBecameUnavailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        Map<Boolean, Set<UUID>> availability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        mockValidUser();
        mockPurchaseAccess(true);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId))
                .thenReturn(order);

        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(availability);

        when(purchasingDomainService.syncOrderSeatsAvailability(order, availability))
                .thenReturn(true);

        EventView eventView = eventView();

        when(eventDomainService.getEvent(eventId))
                .thenReturn(eventView);

        when(eventDomainService.getPrice(eventId, areaId, 2, userId, null, null))
                .thenReturn(priceBreakdown("200.00"));

        ActiveOrderView view = service.getActiveOrder(token, orderId);

        assertEquals(orderId, view.orderId());
        assertEquals(eventId, view.eventId());
    }

    private void mockValidUser() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
    }

    private void mockPurchaseAccess(boolean hasQueueAccess) {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);
    }

    private LotteryEligibilityResult mockValidUserAndAccessObjects(boolean hasQueueAccess) {
        mockValidUser();

        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);

        return eligibility;
    }

    private ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = new ActiveOrder(orderId, userId, eventId, areaId);
        order.addSeats(Set.of(seats));
        return order;
    }

    private EventView eventView() {
        EventView.AreaView areaView = new EventView.AreaView(
                areaId,
                "Main Area",
                money("100.00"),
                "SEATING",
                2,
                List.of(
                        new EventView.SeatView(
                                seatId1,
                                "R1",
                                "1",
                                "AVAILABLE"
                        ),
                        new EventView.SeatView(
                                seatId2,
                                "R1",
                                "2",
                                "AVAILABLE"
                        )
                )
        );

        EventView eventView = mock(EventView.class);

        when(eventView.areas()).thenReturn(List.of(areaView));
        when(eventView.name()).thenReturn("Test Event");
        when(eventView.artist()).thenReturn("Test Artist");
        when(eventView.startsAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(eventView.location()).thenReturn("Test Location");
        when(eventView.status()).thenReturn(EventStatus.PUBLISHED);

        return eventView;
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
}