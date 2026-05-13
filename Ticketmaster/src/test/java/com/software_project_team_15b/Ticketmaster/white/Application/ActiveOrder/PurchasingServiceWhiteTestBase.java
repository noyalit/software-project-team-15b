package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueAccessView;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueAccessStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
abstract class PurchasingServiceWhiteTestBase {

    @Mock
    protected PurchasingDomainService purchasingDomainService;

    @Mock
    protected IMemberRepository memberRepository;

    @Mock
    protected IEventDomainService eventDomainService;

    @Mock
    protected IQueueDomainService queueDomainService;

    @Mock
    protected ILotteryDomainService lotteryDomainService;

    @Mock
    protected IPaymentAPI paymentGateway;

    @Mock
    protected ITicketSupplyAPI ticketProvider;

    @Mock
    protected IAuth auth;

    protected PurchasingService service;

    protected String token;
    protected UUID userId;
    protected UUID eventId;
    protected UUID areaId;
    protected UUID orderId;
    protected UUID seatId1;
    protected UUID seatId2;

    @BeforeEach
    void setUpBase() {
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

    protected void mockValidUser() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
    }

    protected void mockInvalidUser() {
        when(auth.isTokenValid(token)).thenReturn(false);
    }

    protected LotteryEligibilityResult mockLotteryEligibilityResult() {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        return eligibility;
    }

    protected LotteryEligibilityResult mockPurchaseAccessAllowed() {
        LotteryEligibilityResult eligibility = mockLotteryEligibilityResult();

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(true);

        return eligibility;
    }

    protected LotteryEligibilityResult mockPurchaseAccessDeniedByQueue() {
        LotteryEligibilityResult eligibility = mockLotteryEligibilityResult();

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(false);

        return eligibility;
    }

    protected ActiveOrder activeOrder() {
        return new ActiveOrder(orderId, userId, eventId, areaId);
    }

    protected ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = activeOrder();
        order.addSeats(Set.of(seats));
        return order;
    }

    protected ActiveOrder activeOrderInCheckoutWithSeats(UUID... seats) {
        ActiveOrder order = activeOrderWithSeats(seats);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));
        return order;
    }

    protected Money money(String amount) {
        return Money.of(amount, "ILS");
    }

    protected PriceBreakdown priceBreakdown(String total) {
        Money basePrice = money(total);
        Money subtotal = money(total);
        Money discount = money("0.00");
        Money totalPrice = money(total);

        return new PriceBreakdown(basePrice, subtotal, discount, totalPrice);
    }

    protected void mockEventViewWithCurrentArea() {
        EventView.AreaView areaView = new EventView.AreaView(
                areaId,
                "area",
                money("0.00"),
                "SEATING",
                1,
                List.of(
                        new EventView.SeatView(
                                seatId1,
                                "R1",
                                "1",
                                "AVAILABLE"
                        )
                )
        );

        EventView eventView = mock(EventView.class);

        when(eventView.areas()).thenReturn(List.of(areaView));
        when(eventView.name()).thenReturn("evt");
        when(eventView.artist()).thenReturn("artist");
        when(eventView.startsAt()).thenReturn(Instant.now());
        when(eventView.location()).thenReturn("loc");
        when(eventView.status()).thenReturn(EventStatus.PUBLISHED);

        when(eventDomainService.getEvent(eventId)).thenReturn(eventView);
    }

    protected QueueAccessView admittedQueueAccessView() {
        return new QueueAccessView(
                eventId,
                QueueAccessStatus.ADMITTED,
                null,
                LocalDateTime.now().plusMinutes(10)
        );
    }

    protected ActiveOrder expiredActiveOrderMock() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getUserId()).thenReturn(userId);
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsActive(order);

        return order;
    }

    protected ActiveOrder expiredModifiableOrderMock() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getUserId()).thenReturn(userId);
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsModifiable(order);

        return order;
    }

    protected ActiveOrder expiredCheckoutOrderMock() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getUserId()).thenReturn(userId);
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));

        doThrow(new TimeExpiredException("expired"))
                .when(purchasingDomainService)
                .validateOrderIsInCheckout(order);

        return order;
    }

    @SuppressWarnings("unchecked")
    protected Response<Boolean> successfulResponse() {
        Response<Boolean> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    @SuppressWarnings("unchecked")
    protected Response<Boolean> failedResponse(String errorMessage) {
        Response<Boolean> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getErrorMessage()).thenReturn(errorMessage);
        return response;
    }
}