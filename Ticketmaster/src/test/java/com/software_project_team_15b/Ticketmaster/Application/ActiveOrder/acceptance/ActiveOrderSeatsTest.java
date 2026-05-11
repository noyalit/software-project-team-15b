package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveOrderSeatsTest extends PurchasingServiceTestBase {

    @Test
    void addSeatsToExistingOrderShouldAddSeatsWhenAllRequestedSeatsAreAvailable() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        Set<java.util.UUID> requestedSeats = Set.of(seatId1, seatId2);

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

        ActiveOrder order = activeOrder();
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        Set<java.util.UUID> requestedSeats = Set.of(seatId1, seatId2);

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

    @Test
    void addSeatsToExistingOrderShouldThrowWhenQueueAccessIsMissing() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessDenied();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1, seatId2));

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
        assertTrue(order.getOrderSeats().isEmpty());

        verify(eventManagementService, never()).getSeatsAvailability(any(), any(), any());
        verify(activeOrderRepository, never()).save(order);
    }

    @Test
    void addSeatsToExistingOrderShouldThrowWhenLotteryAccessIsMissing() {
        mockValidUser();

        ActiveOrder order = activeOrder();
        mockOrderFoundForUpdate(order);

        mockLotteryDenied();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1, seatId2));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.addSeatsToExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("not eligible"));
        assertTrue(order.getOrderSeats().isEmpty());

        verify(queueService, never()).hasAccess(any(), any());
        verify(eventManagementService, never()).getSeatsAvailability(any(), any(), any());
        verify(activeOrderRepository, never()).save(order);
    }

    @Test
    void removeSeatsFromExistingOrderShouldRemoveSeatsFromOrder() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        service.removeSeatsFromExistingOrder(token, cmd);

        assertEquals(Set.of(seatId2), order.getOrderSeats());
        verify(activeOrderRepository).save(order);
    }

    @Test
    void removeSeatsFromExistingOrderShouldThrowWhenOrderDoesNotExist() {
        mockValidUser();

        when(activeOrderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("Active order not found"));
        verify(activeOrderRepository, never()).save(any());
    }

    @Test
    void removeSeatsFromExistingOrderShouldThrowWhenSeatIsNotReservedByUser() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessAllowed();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId2));

        AlreadyDoneException exception = assertThrows(AlreadyDoneException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("Seats not found in order"));
        assertEquals(Set.of(seatId1), order.getOrderSeats());
        verify(activeOrderRepository, never()).save(order);
    }

    @Test
    void removeSeatsFromExistingOrderShouldThrowWhenQueueAccessIsMissing() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        mockOrderFoundForUpdate(order);

        mockLotteryAllowed();
        mockQueueAccessDenied();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        TimeExpiredException exception = assertThrows(TimeExpiredException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
        assertEquals(Set.of(seatId1, seatId2), order.getOrderSeats());
        verify(activeOrderRepository, never()).save(order);
    }

    @Test
    void removeSeatsFromExistingOrderShouldThrowWhenLotteryAccessIsMissing() {
        mockValidUser();

        ActiveOrder order = activeOrderWithSeats(seatId1, seatId2);
        mockOrderFoundForUpdate(order);

        mockLotteryDenied();

        RemoveOrAddSeatsFromActiveOrderCommand cmd =
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, Set.of(seatId1));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.removeSeatsFromExistingOrder(token, cmd)
        );

        assertTrue(exception.getMessage().contains("not eligible"));
        assertEquals(Set.of(seatId1, seatId2), order.getOrderSeats());

        verify(queueService, never()).hasAccess(any(), any());
        verify(activeOrderRepository, never()).save(order);
    }
}
