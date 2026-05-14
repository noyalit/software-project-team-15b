package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
abstract class PurchasingServiceTestBase {

    @Mock
    protected IActiveOrderRepository activeOrderRepository;

    @Mock
    protected IOrderHistoryRepository orderHistoryRepository;

    @Mock
    protected IMemberRepository memberRepository;

    @Mock
    protected EventManagementService eventManagementService;

    @Mock
    protected QueueService queueService;

    @Mock
    protected LotteryService lotteryService;

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
                activeOrderRepository,
                orderHistoryRepository,
                memberRepository,
                eventManagementService,
                queueService,
                lotteryService,
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

    protected void mockLotteryAllowed() {
        LotteryEligibilityDTO result = mock(LotteryEligibilityDTO.class);

        when(lotteryService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(result);

        when(result.canCreateActiveOrder())
                .thenReturn(true);
    }

    protected void mockLotteryDenied() {
        LotteryEligibilityDTO result = mock(LotteryEligibilityDTO.class);

        when(lotteryService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(result);

        when(result.canCreateActiveOrder())
                .thenReturn(false);
    }

    protected void mockQueueAccessAllowed() {
        when(queueService.hasAccess(token, eventId)).thenReturn(true);
    }

    protected void mockQueueAccessDenied() {
        when(queueService.hasAccess(token, eventId)).thenReturn(false);
    }

    protected void mockOrderFoundForUpdate(ActiveOrder order) {
        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
    }

    protected ActiveOrder activeOrder() {
        return new ActiveOrder(orderId, userId, eventId, areaId);
    }

    protected ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = activeOrder();
        order.addSeats(Set.of(seats));
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
        EventDTO.AreaView areaView = new EventDTO.AreaView(
                areaId,
                "area",
                money("0.00"),
                "SEATING",
                1,
                List.of(
                        new EventDTO.SeatView(
                                seatId1,
                                "R1",
                                "1",
                                "AVAILABLE"
                        )
                )
        );

        EventDTO eventView = mock(EventDTO.class);

        when(eventView.areas()).thenReturn(List.of(areaView));
        when(eventView.name()).thenReturn("evt");
        when(eventView.artist()).thenReturn("artist");
        when(eventView.startsAt()).thenReturn(Instant.now());
        when(eventView.location()).thenReturn("loc");
        when(eventView.status()).thenReturn(EventStatus.PUBLISHED);

        when(eventManagementService.getEvent(eventId))
                .thenReturn(eventView);
    }

    protected ActiveOrder mockExpiredActiveOrder() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(order.getUserId()).thenReturn(userId);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);

        doThrow(new TimeExpiredException("expired"))
                .when(order)
                .ensureOrderIsActive();

        return order;
    }

    protected ActiveOrder mockExpiredCheckoutOrder() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(order.getUserId()).thenReturn(userId);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));
        when(order.getEventId()).thenReturn(eventId);
        when(order.getOrderId()).thenReturn(orderId);

        doThrow(new TimeExpiredException("expired"))
                .when(order)
                .ensureOrderIsInCheckout();

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
