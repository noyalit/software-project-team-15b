package com.software_project_team_15b.Ticketmaster.black.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateActiveOrderBlackTest {

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

    @Mock
    private INotifier notifier;

    private PurchasingService service;

    private String token;
    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID orderId;

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
                auth,
                notifier
        );

        token = "valid-token";
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void createActiveOrderShouldReturnOrderIdWhenUserCanBookEventAreaAndHasAccess() {
        mockValidUser();
        mockEventAvailable();
        mockAreaAvailable();
        mockPurchaseAccess(true);

        when(purchasingDomainService.createActiveOrder(userId, eventId, areaId))
                .thenReturn(orderId);

        UUID result = service.createActiveOrder(token, eventId, areaId);

        assertEquals(orderId, result);
    }

    @Test
    void createActiveOrderShouldFailWhenTokenIsInvalid() {
        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(InvalidTokenException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );
    }

    @Test
    void createActiveOrderShouldFailWhenEventIsNotAvailable() {
        mockValidUser();

        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(null);

        doThrow(new IllegalStateException("Event is not available for booking"))
                .when(purchasingDomainService)
                .requireEventCanBeBooked(eventId, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("Event is not available"));
    }

    @Test
    void createActiveOrderShouldFailWhenAreaIsNotAvailable() {
        mockValidUser();
        mockEventAvailable();

        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(false);

        doThrow(new IllegalStateException("Area is not available for booking"))
                .when(purchasingDomainService)
                .requireAreaCanBeBooked(areaId, false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("Area is not available"));
    }

    @Test
    void createActiveOrderShouldFailWhenUserDoesNotHaveQueueAccess() {
        mockValidUser();
        mockEventAvailable();
        mockAreaAvailable();

        LotteryEligibilityDTO eligibility = mockPurchaseAccess(false);

        doThrow(new com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException(
                "User does not have access"
        ))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, false);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("does not have access"));
    }

    @Test
    void createActiveOrderShouldFailWhenLotteryRejectsUser() {
        mockValidUser();
        mockEventAvailable();
        mockAreaAvailable();

        LotteryEligibilityDTO eligibility = mockPurchaseAccess(true);

        doThrow(new IllegalStateException("User is not eligible"))
                .when(purchasingDomainService)
                .requirePurchaseAccess(userId, eventId, eligibility, true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("not eligible"));
    }

    @Test
    void createActiveOrderShouldFailWhenUserAlreadyHasActiveOrderForEvent() {
        mockValidUser();
        mockEventAvailable();
        mockAreaAvailable();
        mockPurchaseAccess(true);

        when(purchasingDomainService.createActiveOrder(userId, eventId, areaId))
                .thenThrow(new DataIntegrityViolationException("duplicate active order"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.createActiveOrder(token, eventId, areaId)
        );

        assertTrue(exception.getMessage().contains("User already has an active order"));
    }

    @Test
    void requestAccessToCreateActiveOrderShouldReturnQueueAccessWhenTokenIsValid() {
        mockValidUser();

        QueueAccessDTO queueAccess = new QueueAccessDTO(eventId, QueueAccessStatus.NO_QUEUE, null, null);

        when(queueDomainService.requestAccess(token, eventId)).thenReturn(queueAccess);

        QueueAccessDTO result = service.requestAccessToCreateActiveOrder(token, eventId);

        assertEquals(queueAccess, result);
        verify(queueDomainService).requestAccess(token, eventId);
    }

    private void mockValidUser() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
    }

    private void mockEventAvailable() {
        when(eventDomainService.getEventAvailability(eventId))
                .thenReturn(EventAvailability.AVAILABLE);
    }

    private void mockAreaAvailable() {
        when(eventDomainService.getAreaAvailability(eventId, areaId))
                .thenReturn(true);
    }

    private LotteryEligibilityDTO mockPurchaseAccess(boolean hasQueueAccess) {
        LotteryEligibilityDTO eligibility = mock(LotteryEligibilityDTO.class);

        when(lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId))
                .thenReturn(eligibility);

        when(queueDomainService.hasAccess(token, eventId))
                .thenReturn(hasQueueAccess);

        return eligibility;
    }
}