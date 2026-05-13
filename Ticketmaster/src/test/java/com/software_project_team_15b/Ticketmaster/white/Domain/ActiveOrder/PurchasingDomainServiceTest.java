package com.software_project_team_15b.Ticketmaster.white.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchasingDomainServiceTest {

    @Mock
    private IActiveOrderRepository activeOrderRepository;

    @Mock
    private IOrderHistoryRepository orderHistoryRepository;

    private PurchasingDomainService domainService;

    private UUID userId;
    private UUID otherUserId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;
    private UUID seatId1;
    private UUID seatId2;

    @BeforeEach
    void setUp() {
        domainService = new PurchasingDomainService(
                activeOrderRepository,
                orderHistoryRepository
        );

        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();
    }

    @Test
    void createActiveOrderShouldCreateActiveOrderAndSaveIt() {
        UUID createdOrderId = domainService.createActiveOrder(userId, eventId, areaId);

        ArgumentCaptor<ActiveOrder> captor = ArgumentCaptor.forClass(ActiveOrder.class);
        verify(activeOrderRepository).saveAndFlush(captor.capture());

        ActiveOrder savedOrder = captor.getValue();

        assertEquals(createdOrderId, savedOrder.getOrderId());
        assertEquals(userId, savedOrder.getUserId());
        assertEquals(eventId, savedOrder.getEventId());
        assertEquals(areaId, savedOrder.getAreaId());
        assertEquals(ActiveOrderStatus.ACTIVE, savedOrder.getStatus());
    }

    @Test
    void requireEventCanBeBookedShouldPassWhenEventIsAvailable() {
        assertDoesNotThrow(() ->
                domainService.requireEventCanBeBooked(eventId, EventAvailability.AVAILABLE)
        );
    }

    @Test
    void requireEventCanBeBookedShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.requireEventCanBeBooked(null, EventAvailability.AVAILABLE)
        );
    }

    @Test
    void requireEventCanBeBookedShouldThrowWhenEventIsNotAvailable() {
        assertThrows(IllegalStateException.class, () ->
                domainService.requireEventCanBeBooked(eventId, null)
        );
    }

    @Test
    void requireAreaCanBeBookedShouldPassWhenAreaIsAvailable() {
        assertDoesNotThrow(() ->
                domainService.requireAreaCanBeBooked(areaId, true)
        );
    }

    @Test
    void requireAreaCanBeBookedShouldThrowWhenAreaIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.requireAreaCanBeBooked(null, true)
        );
    }

    @Test
    void requireAreaCanBeBookedShouldThrowWhenAreaIsNotAvailable() {
        assertThrows(IllegalStateException.class, () ->
                domainService.requireAreaCanBeBooked(areaId, false)
        );
    }

    @Test
    void requirePurchaseAccessShouldPassWhenLotteryAllowedAndQueueAccessExists() {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(true);

        assertDoesNotThrow(() ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenLotteryDenied() {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(LotteryEligibilityStatus.NOT_SELECTED);

        assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenQueueAccessMissing() {
        LotteryEligibilityResult eligibility = mock(LotteryEligibilityResult.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(true);

        assertThrows(TimeExpiredException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, false)
        );
    }

    @Test
    void getOwnedOrderForUpdateShouldReturnOrderWhenUserOwnsIt() {
        ActiveOrder order = activeOrder();

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        ActiveOrder result = domainService.getOwnedOrderForUpdate(userId, orderId);

        assertSame(order, result);
    }

    @Test
    void getOwnedOrderForUpdateShouldThrowWhenOrderDoesNotExist() {
        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                domainService.getOwnedOrderForUpdate(userId, orderId)
        );
    }

    @Test
    void getOwnedOrderForUpdateShouldThrowWhenUserDoesNotOwnOrder() {
        ActiveOrder order = activeOrder();

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () ->
                domainService.getOwnedOrderForUpdate(otherUserId, orderId)
        );
    }

    @Test
    void addSeatsToOrderShouldAddSeatsAndSave() {
        ActiveOrder order = activeOrder();

        domainService.addSeatsToOrder(order, Set.of(seatId1, seatId2));

        assertEquals(Set.of(seatId1, seatId2), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    @Test
    void addSeatsToOrderShouldThrowWhenSeatIdsAreEmpty() {
        ActiveOrder order = activeOrder();

        assertThrows(IllegalArgumentException.class, () ->
                domainService.addSeatsToOrder(order, Set.of())
        );

        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void removeSeatsFromOrderShouldRemoveSeatsAndSave() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        domainService.removeSeatsFromOrder(order, Set.of(seatId1));

        assertEquals(Set.of(seatId2), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    @Test
    void removeSeatsFromOrderShouldThrowWhenSeatIsNotInOrder() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        assertThrows(AlreadyDoneException.class, () ->
                domainService.removeSeatsFromOrder(order, Set.of(seatId2))
        );

        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void requireRequestedSeatsAvailableShouldReturnAvailableSeatsWhenAllRequestedSeatsAreAvailable() {
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, requestedSeats,
                false, Set.of()
        );

        Set<UUID> result = domainService.requireRequestedSeatsAvailable(
                seatsAvailability,
                requestedSeats
        );

        assertEquals(requestedSeats, result);
    }

    @Test
    void requireRequestedSeatsAvailableShouldThrowWhenSomeSeatsAreUnavailable() {
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        assertThrows(IllegalStateException.class, () ->
                domainService.requireRequestedSeatsAvailable(seatsAvailability, requestedSeats)
        );
    }

    @Test
    void requireRequestedSeatsAvailableShouldThrowWhenAvailabilityResponseIsInconsistent() {
        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of()
        );

        assertThrows(IllegalStateException.class, () ->
                domainService.requireRequestedSeatsAvailable(seatsAvailability, requestedSeats)
        );
    }

    @Test
    void syncOrderSeatsAvailabilityShouldRemoveUnavailableSeatsAndReturnTrue() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1),
                false, Set.of(seatId2)
        );

        boolean result = domainService.syncOrderSeatsAvailability(order, seatsAvailability);

        assertTrue(result);
        assertEquals(Set.of(seatId1), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    @Test
    void syncOrderSeatsAvailabilityShouldReturnFalseWhenAllSeatsAreAvailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        Map<Boolean, Set<UUID>> seatsAvailability = Map.of(
                true, Set.of(seatId1, seatId2),
                false, Set.of()
        );

        boolean result = domainService.syncOrderSeatsAvailability(order, seatsAvailability);

        assertFalse(result);
        assertEquals(Set.of(seatId1, seatId2), order.getOrderSeats());
        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void buildPurchaseRequestShouldCreateRequestFromActiveOrder() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PurchaseRequest request = domainService.buildPurchaseRequest(order, birthDate);

        assertEquals(eventId, request.eventId());
        assertEquals(areaId, request.areaId());
        assertEquals(userId, request.buyerId());
        assertEquals(birthDate, request.buyerBirthDate());
        assertEquals(2, request.quantity());
        assertEquals(Set.of(seatId1, seatId2), Set.copyOf(request.seatIds()));
    }

    @Test
    void startCheckoutShouldSetExpirationAndSaveOrder() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        LocalDateTime before = LocalDateTime.now();

        LocalDateTime expiresAt = domainService.startCheckout(order);

        LocalDateTime after = LocalDateTime.now().plusMinutes(10).plusSeconds(1);

        assertNotNull(order.getExpiresAt());
        assertEquals(expiresAt, order.getExpiresAt());
        assertTrue(expiresAt.isAfter(before));
        assertTrue(expiresAt.isBefore(after));

        verify(activeOrderRepository).save(order);
    }

    @Test
    void finalizeCheckoutShouldCompleteOrderSaveItAndSaveHistory() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        PriceBreakdown pricing = priceBreakdown("100.00");

        ActiveOrder result = domainService.finalizeCheckout(order, pricing);

        assertSame(order, result);
        assertEquals(ActiveOrderStatus.COMPLETED, order.getStatus());

        verify(activeOrderRepository).save(order);
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }

    @Test
    void getActiveOrdersOfUserForUpdateShouldReturnActiveOrders() {
        List<ActiveOrder> orders = List.of(
                activeOrder(),
                new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId)
        );

        when(activeOrderRepository.findByUserIdAndStatusForUpdate(
                userId,
                ActiveOrderStatus.ACTIVE
        )).thenReturn(orders);

        List<ActiveOrder> result = domainService.getActiveOrdersOfUserForUpdate(userId);

        assertEquals(orders, result);
    }

    @Test
    void cancelOrderShouldCancelOrderAndSave() {
        ActiveOrder order = mock(ActiveOrder.class);

        domainService.cancelOrder(order);

        verify(order).cancel();
        verify(activeOrderRepository).save(order);
    }

    @Test
    void expireOrderShouldExpireOrderAndSave() {
        ActiveOrder order = mock(ActiveOrder.class);

        domainService.expireOrder(order);

        verify(order).expire();
        verify(activeOrderRepository).save(order);
    }

    @Test
    void shouldReleaseHoldBeforeCancelShouldReturnTrueOnlyForActiveOrderInCheckoutWithSeats() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        assertTrue(domainService.shouldReleaseHoldBeforeCancel(order));
    }

    @Test
    void shouldReleaseHoldBeforeCancelShouldReturnFalseWhenOrderHasNoExpiration() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        assertFalse(domainService.shouldReleaseHoldBeforeCancel(order));
    }

    @Test
    void shouldReleaseSeatsForExpiredActiveOrderShouldReturnTrueWhenActiveOrderExpiredAndHasSeats() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(order.hasTimeExpired()).thenReturn(true);
        when(order.isExpired()).thenReturn(false);
        when(order.getOrderSeats()).thenReturn(Set.of(seatId1));

        assertTrue(domainService.shouldReleaseSeatsForExpiredActiveOrder(order));
    }

    @Test
    void shouldReleaseSeatsForExpiredActiveOrderShouldReturnFalseWhenOrderHasNoSeats() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(order.hasTimeExpired()).thenReturn(true);
        when(order.isExpired()).thenReturn(false);
        when(order.getOrderSeats()).thenReturn(Set.of());

        assertFalse(domainService.shouldReleaseSeatsForExpiredActiveOrder(order));
    }

    private ActiveOrder activeOrder() {
        return new ActiveOrder(orderId, userId, eventId, areaId);
    }

    private ActiveOrder activeOrderWithSeats(UUID... seats) {
        ActiveOrder order = activeOrder();
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
}