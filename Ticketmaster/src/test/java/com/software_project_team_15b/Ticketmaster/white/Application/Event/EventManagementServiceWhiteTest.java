package com.software_project_team_15b.Ticketmaster.white.Application.Event;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

@ExtendWith(MockitoExtension.class)
class EventManagementServiceWhiteTest {

    @Mock IEventDomainService eventDomainService;
    @Mock UserDomainService userDomainService;
    @Mock EventCancelManager cancelManager;
    @Mock IAuth auth;
    @Mock INotifier notifier;

    EventManagementService service;

    @BeforeEach
    void setUp() {
        service = new EventManagementService(eventDomainService, userDomainService, cancelManager, auth, notifier);
    }

    // -------------------- resolveMemberCallerId branches --------------------

    @Test
    void GivenNullToken_WhenCreateEventWithToken_ThenThrowsInvalidTokenException() {
        assertThatThrownBy(() -> service.createEvent((CreateEventCommand) null, (String) null))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void GivenBlankToken_WhenPublishWithToken_ThenThrowsInvalidTokenException() {
        assertThatThrownBy(() -> service.publish(UUID.randomUUID(), "   "))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void GivenAuthRejectsToken_WhenCancelWithToken_ThenThrowsInvalidTokenException() {
        when(auth.isTokenValid("bad")).thenReturn(false);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), "bad"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void GivenNonMemberToken_WhenUpdateEventWithToken_ThenThrowsUnauthorizedCompanyAction() {
        when(auth.isTokenValid("guest")).thenReturn(true);
        when(auth.isMember("guest")).thenReturn(false);

        assertThatThrownBy(() -> service.updateEvent(UUID.randomUUID(), new UpdateEventCommand(null, null, null, null, null), "guest"))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void GivenAuthReturnsNullUserId_WhenAddAreaWithToken_ThenThrowsInvalidTokenException() {
        when(auth.isTokenValid("t")).thenReturn(true);
        when(auth.isMember("t")).thenReturn(true);
        when(auth.extractUserId("t")).thenReturn(null);

        assertThatThrownBy(() -> service.addArea(UUID.randomUUID(), mockAddAreaCommand(), "t"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void GivenValidToken_WhenCreateEventWithToken_ThenDelegatesToUuidOverload() {
        UUID caller = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(auth.isTokenValid("t")).thenReturn(true);
        when(auth.isMember("t")).thenReturn(true);
        when(auth.extractUserId("t")).thenReturn(caller);
        CreateEventCommand cmd = new CreateEventCommand(
                companyId, "n", "a", null, java.time.Instant.now().plusSeconds(60),
                "loc", List.of(), List.of());
        when(eventDomainService.createEvent(cmd)).thenReturn(created);

        UUID result = service.createEvent(cmd, "t");

        assertThat(result).isEqualTo(created);
        verify(userDomainService).isActiveOwnerOrFounderOrCompanyManager(companyId, caller, ManagerPermission.MANAGE_EVENTS);
    }

    // -------------------- createEvent: rejection logs and rethrows --------------------

    @Test
    void GivenUnauthorizedCaller_WhenCreateEvent_ThenRethrowsAndDoesNotCreate() {
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        CreateEventCommand cmd = new CreateEventCommand(
                company, "n", "a", null, java.time.Instant.now().plusSeconds(60),
                "loc", List.of(), List.of());
        doThrow(new UnauthorizedCompanyActionException("nope"))
                .when(userDomainService).isActiveOwnerOrFounderOrCompanyManager(company, caller, ManagerPermission.MANAGE_EVENTS);

        assertThatThrownBy(() -> service.createEvent(cmd, caller))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
        verify(eventDomainService, never()).createEvent(any());
    }

    @Test
    void GivenNullCmd_WhenCreateEvent_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> service.createEvent((CreateEventCommand) null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNullCallerId_WhenCreateEvent_ThenThrowsNullPointer() {
        CreateEventCommand cmd = new CreateEventCommand(
                UUID.randomUUID(), "n", "a", null, java.time.Instant.now().plusSeconds(60),
                "loc", List.of(), List.of());
        assertThatThrownBy(() -> service.createEvent(cmd, (UUID) null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------- publish / cancel happy + rejection --------------------

    @Test
    void GivenAuthorizedCaller_WhenPublish_ThenDelegatesToDomainService() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.publish(eventId, caller);

        verify(userDomainService).isLegalEventManager(eventId, caller, company, ManagerPermission.MANAGE_EVENTS);
        verify(eventDomainService).publish(eventId);
    }

    @Test
    void GivenDomainFailsPublish_WhenPublish_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new InvalidEventStateException("bad")).when(eventDomainService).publish(eventId);

        assertThatThrownBy(() -> service.publish(eventId, caller))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenAuthorizedCaller_WhenCancel_ThenDelegatesToDomainService() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.cancel(eventId, caller);

        verify(eventDomainService).cancel(eventId);
        verify(cancelManager).cancelEvent(eventId);
    }

    @Test
    void GivenDomainFailsCancel_WhenCancel_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new InvalidEventStateException("bad")).when(eventDomainService).cancel(eventId);

        assertThatThrownBy(() -> service.cancel(eventId, caller))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // -------------------- addArea --------------------

    @Test
    void GivenAuthorizedCaller_WhenAddArea_ThenReturnsAreaId() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        AddAreaCommand cmd = mockAddAreaCommand();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        when(eventDomainService.addArea(eventId, cmd)).thenReturn(areaId);

        UUID result = service.addArea(eventId, cmd, caller);

        assertThat(result).isEqualTo(areaId);
        verify(userDomainService).isLegalEventManager(eventId, caller, company,
                ManagerPermission.CONFIGURE_HALLS_AND_SEATS);
    }

    @Test
    void GivenUnauthorizedCaller_WhenAddArea_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        AddAreaCommand cmd = mockAddAreaCommand();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new UnauthorizedCompanyActionException("nope"))
                .when(userDomainService).isLegalEventManager(eq(eventId), eq(caller), eq(company), any());

        assertThatThrownBy(() -> service.addArea(eventId, cmd, caller))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
        verify(eventDomainService, never()).addArea(any(), any());
    }

    // -------------------- updateEvent / updateArea / removeArea --------------------

    @Test
    void GivenAuthorizedCaller_WhenUpdateEvent_ThenDelegates() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UpdateEventCommand cmd = new UpdateEventCommand(null, null, null, null, null);
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.updateEvent(eventId, cmd, caller);

        verify(eventDomainService).updateEvent(eventId, cmd);
    }

    @Test
    void GivenUnauthorizedCaller_WhenUpdateEvent_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UpdateEventCommand cmd = new UpdateEventCommand(null, null, null, null, null);
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new UnauthorizedCompanyActionException("nope"))
                .when(userDomainService).isLegalEventManager(eq(eventId), eq(caller), eq(company), any());

        assertThatThrownBy(() -> service.updateEvent(eventId, cmd, caller))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
        verify(eventDomainService, never()).updateEvent(any(), any());
    }

    @Test
    void GivenAuthorizedCaller_WhenUpdateArea_ThenDelegates() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UpdateAreaCommand cmd = new UpdateAreaCommand(null, null, null);
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.updateArea(eventId, areaId, cmd, caller);

        verify(eventDomainService).updateArea(eventId, areaId, cmd);
    }

    @Test
    void GivenAuthorizedCaller_WhenRemoveArea_ThenDelegates() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.removeArea(eventId, areaId, caller);

        verify(eventDomainService).removeArea(eventId, areaId);
    }

    @Test
    void GivenDomainRejects_WhenRemoveArea_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new InvalidEventStateException("bad")).when(eventDomainService).removeArea(eventId, areaId);

        assertThatThrownBy(() -> service.removeArea(eventId, areaId, caller))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // -------------------- replace policies --------------------

    @Test
    void GivenAuthorizedCaller_WhenReplacePurchasePolicies_ThenDelegatesAndAuthorizes() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.replacePurchasePolicies(eventId, List.of(), caller);

        verify(userDomainService).isLegalEventManager(eventId, caller, company,
                ManagerPermission.DEFINE_PURCHASE_POLICY);
        verify(eventDomainService, times(1)).replacePurchasePolicies(eq(eventId), any());
    }

    @Test
    void GivenAuthorizedCaller_WhenReplaceDiscountPolicies_ThenDelegatesAndAuthorizes() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);

        service.replaceDiscountPolicies(eventId, List.of(), caller);

        verify(userDomainService).isLegalEventManager(eventId, caller, company,
                ManagerPermission.DEFINE_DISCOUNT_POLICY);
        verify(eventDomainService, times(1)).replaceDiscountPolicies(eq(eventId), any());
    }

    @Test
    void GivenUnauthorizedCaller_WhenReplacePurchasePolicies_ThenRethrows() {
        UUID eventId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        doThrow(new UnauthorizedCompanyActionException("nope"))
                .when(userDomainService).isLegalEventManager(eq(eventId), eq(caller), eq(company), any());

        assertThatThrownBy(() -> service.replacePurchasePolicies(eventId, List.of(), caller))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
        verify(eventDomainService, never()).replacePurchasePolicies(any(), any());
    }

    // -------------------- getEvent / search / availability / queries --------------------

    @Test
    void GivenEventExists_WhenGetEvent_ThenDelegatesToDomain() {
        UUID eventId = UUID.randomUUID();
        service.getEvent(eventId);
        verify(eventDomainService).getEvent(eventId);
    }

    @Test
    void GivenEventNotFound_WhenGetEvent_ThenPropagatesDomainException() {
        UUID eventId = UUID.randomUUID();
        when(eventDomainService.getEvent(eventId)).thenThrow(new InvalidEventStateException("missing"));
        assertThatThrownBy(() -> service.getEvent(eventId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenSearchCriteria_WhenSearch_ThenDelegates() {
        service.search(null);
        verify(eventDomainService).search(null);
    }

    @Test
    void GivenCompanyAndCriteria_WhenSearchInCompany_ThenDelegates() {
        UUID company = UUID.randomUUID();
        service.searchInCompany(company, null);
        verify(eventDomainService).searchInCompany(company, null);
    }

    @Test
    void GivenEventId_WhenGetCompanyIdForEventId_ThenDelegates() {
        UUID eventId = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(eventDomainService.getCompanyIdForEventId(eventId)).thenReturn(company);
        assertThat(service.getCompanyIdForEventId(eventId)).isEqualTo(company);
    }

    @Test
    void GivenEventId_WhenGetPurchasePolicies_ThenReturnsDtoList() {
        UUID eventId = UUID.randomUUID();
        when(eventDomainService.getPurchasePolicies(eventId)).thenReturn(List.of());
        assertThat(service.getPurchasePolicies(eventId)).isEmpty();
    }

    @Test
    void GivenEventId_WhenGetDiscountPolicies_ThenReturnsDtoList() {
        UUID eventId = UUID.randomUUID();
        when(eventDomainService.getDiscountPolicies(eventId)).thenReturn(List.of());
        assertThat(service.getDiscountPolicies(eventId)).isEmpty();
    }

    @Test
    void GivenNullEventId_WhenGetPurchasePolicies_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> service.getPurchasePolicies(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNullEventId_WhenGetDiscountPolicies_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> service.getDiscountPolicies(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------- notifyEventIsCancelled (subscriber callback) --------------------

    @Test
    void GivenEventId_WhenNotifyEventIsCancelled_ThenNotifiesAttendeesWithoutReCancelling() {
        UUID eventId = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        when(eventDomainService.collectAttendeeUserIds(eventId)).thenReturn(List.of(attendee));

        service.notifyEventIsCancelled(eventId);

        // The status transition is owned by cancel(); this subscriber must not re-cancel.
        verify(eventDomainService, never()).cancel(eventId);
        verify(notifier).notifyUser(eq(attendee), any());
    }

    @Test
    void GivenNullEventId_WhenNotifyEventIsCancelled_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> service.notifyEventIsCancelled(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------- Constructor: subscribe failure --------------------

    @Test
    void GivenCancelManagerSubscribeFails_WhenConstruct_ThenThrowsRuntimeException() {
        EventCancelManager bad = org.mockito.Mockito.mock(EventCancelManager.class);
        doThrow(new IllegalStateException("subscribe failed")).when(bad).subscribe(any());

        assertThatThrownBy(() -> new EventManagementService(
                eventDomainService, userDomainService, bad, auth, notifier))
                .isInstanceOf(RuntimeException.class);
    }

    // -------------------- helper --------------------

    private static AddAreaCommand mockAddAreaCommand() {
        return new AddAreaCommand(
                "Main",
                new com.software_project_team_15b.Ticketmaster.Domain.Event.Money(
                        new java.math.BigDecimal("10.00"), "USD"),
                AddAreaCommand.AreaType.STANDING,
                10,
                List.of());
    }
}
