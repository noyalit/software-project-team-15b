package com.software_project_team_15b.Ticketmaster.white.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
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
    void requirePurchaseAccessShouldRejectEachNullArgument() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);

        assertThrows(IllegalArgumentException.class, () ->
                domainService.requirePurchaseAccess(null, eventId, eligibility, true)
        );
        assertThrows(IllegalArgumentException.class, () ->
                domainService.requirePurchaseAccess(userId, null, eligibility, true)
        );
        assertThrows(IllegalArgumentException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, null, true)
        );
    }

    @Test
    void requireRequestedSeatsAvailableShouldRejectNullAvailability() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.requireRequestedSeatsAvailable(null, Set.of(seatId1))
        );
    }

    @Test
    void syncOrderSeatsAvailabilityShouldRejectNullAvailabilityForOrderWithSeats() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1));

        assertThrows(IllegalArgumentException.class, () ->
                domainService.syncOrderSeatsAvailability(activeOrder, null)
        );
    }

    @Test
    void finalizeCheckoutShouldRejectNullPricing() {
        ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        activeOrder.addSeats(Set.of(seatId1));
        activeOrder.startCheckout(LocalDateTime.now().plusMinutes(10));

        Integer paymentTransactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(
                seatId1, "TICKET-1"
        );

        assertThrows(IllegalArgumentException.class, () ->
                domainService.finalizeCheckout(
                        activeOrder,
                        paymentTransactionId,
                        null,
                        issuedTicketIds
                )
        );
    }

    @Test
    void getActiveOrdersOfUserForUpdateShouldRejectNullUserId() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.getActiveOrdersOfUserForUpdate(null)
        );
    }

    @Test
    void activeOrderArgumentsAndSeatIdsShouldRejectNulls() {
        Set<UUID> seatsWithNull = new java.util.HashSet<>();
        seatsWithNull.add(null);

        assertThrows(IllegalArgumentException.class, () ->
                domainService.validateOrderIsActive(null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                domainService.addSeatsToOrder(new ActiveOrder(orderId, userId, eventId, areaId), seatsWithNull)
        );
    }

    @Test
    void releaseDecisionHelpersShouldCoverFalseBranches() {
        ActiveOrder canceledOrder = new ActiveOrder(orderId, userId, eventId, areaId);
        canceledOrder.cancel();

        ActiveOrder checkoutWithoutSeats = new ActiveOrder(UUID.randomUUID(), userId, eventId, areaId);

        assertFalse(domainService.shouldReleaseSeatsForExpiredActiveOrder(canceledOrder));
        assertFalse(domainService.shouldReleaseHoldBeforeCancel(checkoutWithoutSeats));
    }

    @Test
    void releaseDecisionHelpersShouldCoverShortCircuitBranches() {
        ActiveOrder activeNotExpired = mock(ActiveOrder.class);
        when(activeNotExpired.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(activeNotExpired.hasTimeExpired()).thenReturn(false);
        assertFalse(domainService.shouldReleaseSeatsForExpiredActiveOrder(activeNotExpired));

        ActiveOrder activeAlreadyExpired = mock(ActiveOrder.class);
        when(activeAlreadyExpired.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(activeAlreadyExpired.hasTimeExpired()).thenReturn(true);
        when(activeAlreadyExpired.isExpired()).thenReturn(true);
        assertFalse(domainService.shouldReleaseSeatsForExpiredActiveOrder(activeAlreadyExpired));

        ActiveOrder checkoutWithoutSeats = mock(ActiveOrder.class);
        when(checkoutWithoutSeats.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(checkoutWithoutSeats.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
        when(checkoutWithoutSeats.getOrderSeats()).thenReturn(Set.of());
        assertFalse(domainService.shouldReleaseHoldBeforeCancel(checkoutWithoutSeats));
    }

    @Test
    void requireSeatIdsShouldRejectNullAndEmptySets() {
        ActiveOrder order = activeOrder();

        assertThrows(IllegalArgumentException.class, () ->
                domainService.addSeatsToOrder(order, null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                domainService.addSeatsToOrder(order, Set.of())
        );
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
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(true);

        assertDoesNotThrow(() ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenLotteryDenied() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(LotteryEligibilityStatus.NOT_SELECTED);

        assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenQueueAccessMissing() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
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
        Integer paymentTransactionId = 12345;
        Map<UUID, String> issuedTicketIds = Map.of(
                seatId1, "TICKET-1"
        );

        domainService.finalizeCheckout(
                order,
                paymentTransactionId,
                pricing,
                issuedTicketIds
        );

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

    @Test
    void shouldReleaseHoldBeforeCancelShouldReturnFalseWhenOrderHasNoSeats() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getStatus()).thenReturn(ActiveOrderStatus.ACTIVE);
        when(order.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
        when(order.getOrderSeats()).thenReturn(Set.of());

        assertFalse(domainService.shouldReleaseHoldBeforeCancel(order));
    }

    @Test
    void shouldReleaseHoldBeforeCancelShouldReturnFalseWhenOrderIsNotActive() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getStatus()).thenReturn(ActiveOrderStatus.CANCELED);

        assertFalse(domainService.shouldReleaseHoldBeforeCancel(order));
        verify(order, never()).getExpiresAt();
        verify(order, never()).getOrderSeats();
    }

    @Test
    void validateOrderIsActiveShouldPassForActiveOrder() {
        assertDoesNotThrow(() -> domainService.validateOrderIsActive(activeOrder()));
    }

    @Test
    void validateOrderIsActiveShouldThrowWhenOrderHasExpiredTime() {
        ActiveOrder order = mock(ActiveOrder.class);

        doThrow(new TimeExpiredException("expired"))
                .when(order)
                .ensureOrderIsActive();

        assertThrows(TimeExpiredException.class, () -> domainService.validateOrderIsActive(order));
    }

    @Test
    void validateOrderIsModifiableShouldPassForOrderWithoutCheckout() {
        assertDoesNotThrow(() -> domainService.validateOrderIsModifiable(activeOrder()));
    }

    @Test
    void validateOrderIsInCheckoutShouldPassForOrderInCheckout() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        assertDoesNotThrow(() -> domainService.validateOrderIsInCheckout(order));
    }

    @Test
    void getOwnedActiveOrderForUpdateShouldReturnOrderWhenOrderIsActive() {
        ActiveOrder order = activeOrder();

        when(activeOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ActiveOrder result = domainService.getOwnedActiveOrderForUpdate(userId, orderId);

        assertSame(order, result);
    }

    @Test
    void getOwnedModifiableOrderForUpdateShouldReturnOrderWhenOrderIsModifiable() {
        ActiveOrder order = activeOrder();

        when(activeOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ActiveOrder result = domainService.getOwnedModifiableOrderForUpdate(userId, orderId);

        assertSame(order, result);
    }

    @Test
    void getOwnedCheckoutOrderForUpdateShouldReturnOrderWhenOrderIsInCheckout() {
        ActiveOrder order = activeOrderWithSeats(seatId1);
        order.startCheckout(LocalDateTime.now().plusMinutes(10));

        when(activeOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ActiveOrder result = domainService.getOwnedCheckoutOrderForUpdate(userId, orderId);

        assertSame(order, result);
    }

    @Test
    void removeUnavailableSeatsFromOrderShouldReturnFalseWhenNoSeatsAreUnavailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        boolean result = domainService.removeUnavailableSeatsFromOrder(order, Set.of());

        assertFalse(result);
        assertEquals(Set.of(seatId1, seatId2), order.getOrderSeats());
        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void removeUnavailableSeatsFromOrderShouldThrowWhenUnavailableSeatsAreNull() {
        ActiveOrder order = activeOrderWithSeats(seatId1);

        assertThrows(IllegalArgumentException.class, () ->
                domainService.removeUnavailableSeatsFromOrder(order, null)
        );
    }

    @Test
    void syncOrderSeatsAvailabilityShouldReturnFalseWhenOrderHasNoSeats() {
        ActiveOrder order = activeOrder();

        boolean result = domainService.syncOrderSeatsAvailability(
                order,
                Map.of(true, Set.of(seatId1), false, Set.of())
        );

        assertFalse(result);
        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void existsActiveOrderForEventShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.existsActiveOrderForEvent(null)
        );
    }

    @Test
    void existsActiveOrderForEventShouldReturnRepositoryResult() {
        when(activeOrderRepository.existsByEventIdAndStatus(eventId, ActiveOrderStatus.ACTIVE))
                .thenReturn(true);

        assertTrue(domainService.existsActiveOrderForEvent(eventId));

        when(activeOrderRepository.existsByEventIdAndStatus(eventId, ActiveOrderStatus.ACTIVE))
                .thenReturn(false);

        assertFalse(domainService.existsActiveOrderForEvent(eventId));
    }

    @Test
    void countActiveOrdersForEventShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.countActiveOrdersForEvent(null)
        );
    }

    @Test
    void countActiveOrdersForEventShouldReturnRepositoryResult() {
        when(activeOrderRepository.countByEventIdAndStatus(eventId, ActiveOrderStatus.ACTIVE))
                .thenReturn(3L);

        assertEquals(3L, domainService.countActiveOrdersForEvent(eventId));
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenStatusIsLotteryOpenNotEntered() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus.LOTTERY_OPEN_NOT_ENTERED);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
        assertTrue(ex.getMessage().contains("lottery draw"));
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenStatusIsLotteryOpenEntered() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus.LOTTERY_OPEN_ENTERED);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
        assertTrue(ex.getMessage().contains("lottery draw"));
    }

    @Test
    void requirePurchaseAccessShouldThrowWhenStatusIsAccessExpired() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus.ACCESS_EXPIRED);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void requirePurchaseAccessShouldThrowFallbackWhenStatusIsUnknown() {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);
        when(eligibility.canCreateActiveOrder()).thenReturn(false);
        when(eligibility.status()).thenReturn(com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus.WON_AND_ACCESS_VALID);

        assertThrows(IllegalStateException.class, () ->
                domainService.requirePurchaseAccess(userId, eventId, eligibility, true)
        );
    }

    @Test
    void findByUserIdAndStatusShouldThrowWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.findByUserIdAndStatus(null, ActiveOrderStatus.ACTIVE)
        );
    }

    @Test
    void findByUserIdAndStatusShouldThrowWhenStatusIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                domainService.findByUserIdAndStatus(userId, null)
        );
    }

    @Test
    void findByUserIdAndStatusShouldReturnOrdersFromRepository() {
        List<ActiveOrder> orders = List.of(activeOrder());
        when(activeOrderRepository.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE))
                .thenReturn(orders);

        List<ActiveOrder> result = domainService.findByUserIdAndStatus(userId, ActiveOrderStatus.ACTIVE);

        assertEquals(orders, result);
    }

    @Test
    void removeUnavailableSeatsFromOrderShouldRemoveSeatsAndReturnTrueWhenSeatsUnavailable() {
        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);

        boolean result = domainService.removeUnavailableSeatsFromOrder(order, Set.of(seatId1));

        assertTrue(result);
        assertEquals(Set.of(seatId2), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
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
