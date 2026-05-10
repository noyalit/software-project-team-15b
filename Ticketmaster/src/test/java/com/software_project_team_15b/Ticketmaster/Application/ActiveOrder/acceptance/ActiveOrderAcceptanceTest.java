package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.acceptance;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.storage.mode=memory"
})
class ActiveOrderAcceptanceTest {

    @jakarta.annotation.Resource
    private PurchasingService purchasingService;

    @jakarta.annotation.Resource
    private IActiveOrderRepository activeOrderRepository;

    @MockitoBean
    private IAuth auth;

    @MockitoBean
    private EventManagementService eventManagementService;

    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private IPaymentAPI paymentGateway;

    @MockitoBean
    private ITicketSupplyAPI ticketProvider;

    @MockitoBean
    private IOrderHistoryRepository orderHistoryRepository;

    private String token;
    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID seatId1;
    private UUID seatId2;

    @BeforeEach
    void setUp() {
        activeOrderRepository.deleteAll(activeOrderRepository.findAll());

        token = "valid-token";
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        seatId1 = UUID.randomUUID();
        seatId2 = UUID.randomUUID();

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);

        when(queueService.hasAccess(token, eventId)).thenReturn(true);

        when(eventManagementService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);

        when(eventManagementService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);
    }

    @Test
    void userCanCreateActiveOrderAndAddSeats_whenAllPreconditionsHold() {
        UUID orderId = purchasingService.createActiveOrder(token, eventId, areaId);

        Set<UUID> seats = Set.of(seatId1, seatId2);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, seats))
                .thenReturn(Map.of(
                        true, seats,
                        false, Set.of()
                ));

        purchasingService.addSeatsToExistingOrder(
                token,
                new RemoveOrAddSeatsFromActiveOrderCommand(orderId, seats)
        );

        ActiveOrder order = activeOrderRepository.findById(orderId).orElseThrow();

        assertEquals(userId, order.getUserId());
        assertEquals(eventId, order.getEventId());
        assertEquals(areaId, order.getAreaId());
        assertEquals(ActiveOrderStatus.ACTIVE, order.getStatus());
        assertEquals(seats, order.getOrderSeats());
        assertNull(order.getExpiresAt());
    }

    @Test
    void userCannotCreateActiveOrder_whenQueueAccessIsMissing() {
        when(queueService.hasAccess(token, eventId)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                purchasingService.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(activeOrderRepository.findAll().isEmpty());
    }

    @Test
    void addSeatsFailsWithoutPartialUpdate_whenOneSeatIsUnavailable() {
        UUID orderId = purchasingService.createActiveOrder(token, eventId, areaId);

        Set<UUID> requestedSeats = Set.of(seatId1, seatId2);

        when(eventManagementService.getSeatsAvailability(eventId, areaId, requestedSeats))
                .thenReturn(Map.of(
                        true, Set.of(seatId1),
                        false, Set.of(seatId2)
                ));

        assertThrows(IllegalStateException.class, () ->
                purchasingService.addSeatsToExistingOrder(
                        token,
                        new RemoveOrAddSeatsFromActiveOrderCommand(orderId, requestedSeats)
                )
        );

        ActiveOrder order = activeOrderRepository.findById(orderId).orElseThrow();

        assertTrue(order.getOrderSeats().isEmpty());
    }
}
