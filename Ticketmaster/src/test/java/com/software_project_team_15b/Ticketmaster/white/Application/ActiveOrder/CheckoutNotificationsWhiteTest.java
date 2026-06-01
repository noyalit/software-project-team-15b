package com.software_project_team_15b.Ticketmaster.white.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutNotificationsWhiteTest extends PurchasingServiceWhiteTestBase {

    @Test
    void completeCheckoutShouldSendPurchaseSuccessNotificationWhenEventIsNotSoldOut() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");
        Response<Boolean> paymentOk = successfulResponse();
        Response<Boolean> ticketsOk = successfulResponse();
        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null)).thenReturn(priceBreakdown);
        when(paymentGateway.chargePayment(token, total)).thenReturn(paymentOk);
        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1))).thenReturn(ticketsOk);
        when(purchasingDomainService.finalizeCheckout(order, priceBreakdown)).thenReturn(order);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);
        when(eventDomainService.confirm(eventId, orderId)).thenReturn(receipt);

        EventDTO.AreaView area = new EventDTO.AreaView(areaId, "area", moneyDTO("100.00"), "SEATING", 5, List.of());
        EventDTO event = mock(EventDTO.class);
        when(event.areas()).thenReturn(List.of(area));
        when(event.name()).thenReturn("Concert");
        when(eventDomainService.getEvent(eventId)).thenReturn(event);

        service.completeCheckoutForGuest(token, orderId, birthDate, null);

        verify(notifier).notifyUser(eq(userId), any(NotificationDTO.class));
        verify(notifier, never()).notifyEventManagers(any(), any());
    }

    @Test
    void completeCheckoutShouldSendSoldOutNotificationWhenEventCapacityReachesZero() {
        mockValidGuest();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        UUID managerId = UUID.randomUUID();

        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);

        PriceBreakdown priceBreakdown = priceBreakdown("100.00");
        Money total = money("100.00");
        Response<Boolean> paymentOk = successfulResponse();
        Response<Boolean> ticketsOk = successfulResponse();
        when(eventDomainService.getPrice(eventId, areaId, 1, userId, birthDate, null)).thenReturn(priceBreakdown);
        when(paymentGateway.chargePayment(token, total)).thenReturn(paymentOk);
        when(ticketProvider.issueTickets(eventId, areaId, Set.of(seatId1))).thenReturn(ticketsOk);
        when(purchasingDomainService.finalizeCheckout(order, priceBreakdown)).thenReturn(order);

        ConfirmationReceipt receipt = mock(ConfirmationReceipt.class);
        when(receipt.areaId()).thenReturn(areaId);
        when(receipt.quantity()).thenReturn(1);
        when(eventDomainService.confirm(eventId, orderId)).thenReturn(receipt);

        EventDTO.AreaView soldOutArea = new EventDTO.AreaView(areaId, "area", moneyDTO("100.00"), "SEATING", 0, List.of());
        EventDTO event = mock(EventDTO.class);
        when(event.areas()).thenReturn(List.of(soldOutArea));
        when(event.name()).thenReturn("Concert");
        when(eventDomainService.getEvent(eventId)).thenReturn(event);
        when(userDomainService.getApprovedEventManagerUserIds(eventId)).thenReturn(Set.of(managerId));

        service.completeCheckoutForGuest(token, orderId, birthDate, null);

        verify(notifier).notifyUser(eq(userId), argThat(n -> n.getType() == NotificationType.PURCHASE_SUCCESS));
        verify(notifier).notifyEventManagers(eq(eventId), any(NotificationDTO.class));
        verify(notifier).notifyUser(eq(managerId), any(NotificationDTO.class));
    }

    @Test
    void getActiveOrderShouldReturnViewForOrderInCheckoutWithAllSeatsAvailable() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(true, Set.of(seatId1), false, Set.of());
        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        mockEventDTOWithCurrentArea();
        when(eventDomainService.getPrice(eq(eventId), eq(areaId), eq(1), eq(userId), isNull(), isNull()))
                .thenReturn(priceBreakdown("0.00"));

        service.getActiveOrder(token, orderId);

        verify(eventDomainService).getSeatsAvailability(eventId, areaId, order.getOrderSeats());
        verify(purchasingDomainService, never()).syncOrderSeatsAvailability(any(), any());
    }

    @Test
    void getActiveOrderShouldThrowWhenOrderIsInCheckoutAndSeatsBecameUnavailable() {
        mockValidUser();

        ActiveOrder order = activeOrderInCheckoutWithSeats(seatId1);
        when(purchasingDomainService.getOwnedOrderForUpdate(userId, orderId)).thenReturn(order);
        mockPurchaseAccessAllowed();

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(true, Set.of(), false, Set.of(seatId1));
        when(eventDomainService.getSeatsAvailability(eventId, areaId, order.getOrderSeats()))
                .thenReturn(seatsAvailability);

        assertThrows(OrderSeatsUnavailableException.class, () ->
                service.getActiveOrder(token, orderId)
        );

        verify(purchasingDomainService, never()).syncOrderSeatsAvailability(any(), any());
    }
}
