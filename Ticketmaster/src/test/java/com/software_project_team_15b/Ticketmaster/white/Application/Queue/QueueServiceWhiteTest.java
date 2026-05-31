package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.software_project_team_15b.Ticketmaster.Application.events.TempTokenAcceptedFromQueueEvent;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.SiteQueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

/**
 * White-box tests for the {@link QueueService} application facade.
 *
 * <p>These tests verify the internal wiring of the facade:
 * <ul>
 *   <li>Token validation via {@link IAuth#isTokenValid} occurs before any domain-service
 *       call for {@code getQueueAccessView}, and invalid or null tokens short-circuit
 *       execution.</li>
 *   <li>{@code getQueueAccessView} is forwarded to the domain service after token
 *       validation, without mutating or substituting arguments.</li>
 *   <li>{@code createEventQueue}, {@code deleteEventQueue}, {@code clearEventQueue},
 *       {@code getQueueSnapshot}, {@code updateEventQueueSettings}, and
 *       {@code getAllQueueSnapshots} all call {@link IAuth#isSystemAdmin} before
 *       delegating to the domain service.</li>
 * </ul>
 *
 * <p>After each test, {@code verifyNoMoreInteractions} on the domain-service mock guards
 * against accidental extra delegations.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceWhiteTest {

    @Mock private IQueueDomainService queueDomainService;
    @Mock private IAuth auth;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private QueueService service;

    private static final UUID   EVENT_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_TOKEN = "admin-token";

    @AfterEach
    void verifyNoUnexpectedDomainServiceInteractions() {
        verifyNoMoreInteractions(queueDomainService);
    }

    // =========================================================================
    // getQueueAccessView — validate token, then delegate
    // =========================================================================

    /**
     * Verifies that {@link IAuth#isTokenValid} is called before the domain-service
     * delegation, and that the domain-service return value is passed through unchanged.
     */
    @Test
    void getQueueAccessView_validatesToken_thenDelegates() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid("token-a");
        inOrder.verify(queueDomainService).getQueueAccessView("token-a", EVENT_ID);
    }

    /**
     * Verifies that {@code getQueueAccessView} passes token and eventId to the domain
     * service without modification or substitution.
     */
    @Test
    void getQueueAccessView_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        String tok = "tok";
        QueueAccessDTO out = new QueueAccessDTO(eid, QueueAccessStatus.NO_QUEUE, null, null);
        when(auth.isTokenValid(tok)).thenReturn(true);
        when(queueDomainService.getQueueAccessView(tok, eid)).thenReturn(out);

        assertThat(service.getQueueAccessView(tok, eid)).isSameAs(out);
        verify(queueDomainService).getQueueAccessView(tok, eid);
    }

    // =========================================================================
    // Admin-gated mutations — isSystemAdmin check precedes domain delegation
    // =========================================================================

    /**
     * Verifies that {@code createEventQueue} validates the token, then checks system-admin
     * status, then delegates to the domain service.
     */
    @Test
    void createEventQueue_checksAdminThenDelegates() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 1000, 100);

        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).createEventQueue(EVENT_ID, 1000, 100);
    }

    /**
     * Verifies that {@code deleteEventQueue} validates the token, then checks system-admin
     * status, then delegates to the domain service.
     */
    @Test
    void deleteEventQueue_checksAdminThenDelegates() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID);

        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).deleteEventQueue(EVENT_ID);
    }

    /**
     * Verifies that a {@link QueueNotFoundException} thrown by the domain service
     * propagates unchanged through {@code deleteEventQueue}.
     */
    @Test
    void deleteEventQueue_propagatesDomainServiceException() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing")).when(queueDomainService).deleteEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
        verify(auth).isTokenValid(ADMIN_TOKEN);
        verify(auth).isSystemAdmin(ADMIN_TOKEN);
        verify(queueDomainService).deleteEventQueue(EVENT_ID);
    }

    /**
     * Verifies that {@code clearEventQueue} validates the token, then checks system-admin
     * status, then delegates to the domain service.
     */
    @Test
    void clearEventQueue_checksAdminThenDelegates() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        service.clearEventQueue(ADMIN_TOKEN, EVENT_ID);

        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).clearEventQueue(EVENT_ID);
    }

    /**
     * Verifies that {@code getQueueSnapshot} validates the token, checks system-admin
     * status, delegates to the domain service, and returns the result unchanged.
     */
    @Test
    void getQueueSnapshot_checksAdminThenDelegatesAndReturnsSnapshot() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        QueueSnapshotDTO expected = new QueueSnapshotDTO(EVENT_ID, 100, 10, 5, 3, Map.of());
        when(queueDomainService.getQueueSnapshot(EVENT_ID)).thenReturn(expected);

        QueueSnapshotDTO result = service.getQueueSnapshot(ADMIN_TOKEN, EVENT_ID);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).getQueueSnapshot(EVENT_ID);
    }

    /**
     * Verifies that {@code updateEventQueueSettings} validates the token, checks system-admin
     * status, and forwards all arguments to the domain service without substitution.
     */
    @Test
    void updateEventQueueSettings_checksAdminThenDelegatesWithExactArgs() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 200, 20);

        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).updateQueueSettings(EVENT_ID, 200, 20);
    }

    /**
     * Verifies that {@code getAllQueueSnapshots} validates the token, checks system-admin
     * status, delegates to the domain service, and returns the result unchanged.
     */
    @Test
    void getAllQueueSnapshots_checksAdminThenDelegatesAndReturnsList() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        QueueSnapshotDTO snap = new QueueSnapshotDTO(EVENT_ID, 100, 10, 5, 3, Map.of());
        List<QueueSnapshotDTO> expected = List.of(snap);
        when(queueDomainService.getAllQueueSnapshots()).thenReturn(expected);

        List<QueueSnapshotDTO> result = service.getAllQueueSnapshots(ADMIN_TOKEN);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).getAllQueueSnapshots();
    }

    // =========================================================================
    // constructor null checks
    // =========================================================================

    @Test
    void constructor_throws_when_queueDomainService_is_null() {
        assertThatThrownBy(() -> new com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService(null, auth, eventPublisher))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_eventPublisher_is_null() {
        assertThatThrownBy(() -> new com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService(queueDomainService, auth, null))
                .isInstanceOf(NullPointerException.class);
    }

    // =========================================================================
    // private acceptUsersFromSiteQueue — all branches via reflection
    // =========================================================================

    private void invokeAcceptUsersFromSiteQueue() throws Exception {
        Method m = com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService.class
                .getDeclaredMethod("acceptUsersFromSiteQueue");
        m.setAccessible(true);
        m.invoke(service);
    }

    @Test
    void scheduledAccept_emptyAdmittedSet_noEvictionNoEvent() throws Exception {
        when(queueDomainService.getAcceptedTokens()).thenReturn(Set.of()).thenReturn(Set.of());

        assertThatCode(this::invokeAcceptUsersFromSiteQueue).doesNotThrowAnyException();

        verify(queueDomainService, times(2)).getAcceptedTokens();
        verify(queueDomainService).acceptUsersFromSiteQueue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void scheduledAccept_invalidToken_evictedBeforeAdmit() throws Exception {
        when(queueDomainService.getAcceptedTokens())
                .thenReturn(Set.of("expired-tok"))
                .thenReturn(Set.of());
        when(auth.isTokenValid("expired-tok")).thenReturn(false);

        invokeAcceptUsersFromSiteQueue();

        verify(queueDomainService, times(2)).getAcceptedTokens();
        verify(auth).isTokenValid("expired-tok");
        verify(queueDomainService).removeAcceptedToken("expired-tok");
        verify(queueDomainService).acceptUsersFromSiteQueue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void scheduledAccept_validToken_notEvicted() throws Exception {
        when(queueDomainService.getAcceptedTokens())
                .thenReturn(Set.of("valid-tok"))
                .thenReturn(Set.of("valid-tok"));
        when(auth.isTokenValid("valid-tok")).thenReturn(true);

        invokeAcceptUsersFromSiteQueue();

        verify(queueDomainService, times(2)).getAcceptedTokens();
        verify(auth).isTokenValid("valid-tok");
        verify(queueDomainService, never()).removeAcceptedToken(any());
        verify(queueDomainService).acceptUsersFromSiteQueue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void scheduledAccept_newlyAdmittedToken_publishesEvent() throws Exception {
        when(queueDomainService.getAcceptedTokens())
                .thenReturn(Set.of())
                .thenReturn(Set.of("new-tok"));

        invokeAcceptUsersFromSiteQueue();

        verify(queueDomainService, times(2)).getAcceptedTokens();
        verify(queueDomainService).acceptUsersFromSiteQueue();
        verify(eventPublisher).publishEvent(any(TempTokenAcceptedFromQueueEvent.class));
    }

    // =========================================================================
    // getQueueAccessView — null / invalid token guards
    // =========================================================================

    @Test
    void getQueueAccessView_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.getQueueAccessView(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueAccessView_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.getQueueAccessView("tok", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueAccessView_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid("bad-tok")).thenReturn(false);
        assertThatThrownBy(() -> service.getQueueAccessView("bad-tok", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // createEventQueue — guard paths
    // =========================================================================

    @Test
    void createEventQueue_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.createEventQueue(null, EVENT_ID, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_throwsWhenCapacityIsNegative() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_throwsWhenMaxAcceptedIsNegative() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 100, 10))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createEventQueue_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 100, 10))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // deleteEventQueue — guard paths
    // =========================================================================

    @Test
    void deleteEventQueue_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.deleteEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventQueue_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.deleteEventQueue(ADMIN_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventQueue_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void deleteEventQueue_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // clearEventQueue — guard paths
    // =========================================================================

    @Test
    void clearEventQueue_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.clearEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearEventQueue_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.clearEventQueue(ADMIN_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearEventQueue_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.clearEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void clearEventQueue_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.clearEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // getQueueSnapshot — guard paths
    // =========================================================================

    @Test
    void getQueueSnapshot_throwsWhenAdminTokenIsNull() {
        assertThatThrownBy(() -> service.getQueueSnapshot(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueSnapshot_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.getQueueSnapshot(ADMIN_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueSnapshot_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getQueueSnapshot(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getQueueSnapshot_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getQueueSnapshot(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // updateEventQueueSettings — guard paths
    // =========================================================================

    @Test
    void updateEventQueueSettings_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(null, EVENT_ID, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEventQueueSettings_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEventQueueSettings_throwsWhenCapacityIsNegative() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEventQueueSettings_throwsWhenMaxAcceptedIsNegative() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEventQueueSettings_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 100, 10))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void updateEventQueueSettings_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 100, 10))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // getAllQueueSnapshots — guard paths
    // =========================================================================

    @Test
    void getAllQueueSnapshots_throwsWhenAdminTokenIsNull() {
        assertThatThrownBy(() -> service.getAllQueueSnapshots(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAllQueueSnapshots_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getAllQueueSnapshots(ADMIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getAllQueueSnapshots_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getAllQueueSnapshots(ADMIN_TOKEN))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // getSiteQueueSnapshot — happy path + guard paths
    // =========================================================================

    @Test
    void getSiteQueueSnapshot_checksAdminThenDelegatesAndReturnsSnapshot() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        SiteQueueSnapshotDTO expected = new SiteQueueSnapshotDTO(500, 10, 3);
        when(queueDomainService.getSiteQueueSnapshot()).thenReturn(expected);

        SiteQueueSnapshotDTO result = service.getSiteQueueSnapshot(ADMIN_TOKEN);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).getSiteQueueSnapshot();
    }

    @Test
    void getSiteQueueSnapshot_throwsWhenAdminTokenIsNull() {
        assertThatThrownBy(() -> service.getSiteQueueSnapshot(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getSiteQueueSnapshot_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getSiteQueueSnapshot(ADMIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getSiteQueueSnapshot_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.getSiteQueueSnapshot(ADMIN_TOKEN))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // updateSiteQueueSettings — happy path + guard paths
    // =========================================================================

    @Test
    void updateSiteQueueSettings_checksAdminThenDelegatesAndReturnsSnapshot() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        SiteQueueSnapshotDTO expected = new SiteQueueSnapshotDTO(200, 5, 2);
        when(queueDomainService.getSiteQueueSnapshot()).thenReturn(expected);

        SiteQueueSnapshotDTO result = service.updateSiteQueueSettings(ADMIN_TOKEN, 200);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid(ADMIN_TOKEN);
        inOrder.verify(auth).isSystemAdmin(ADMIN_TOKEN);
        inOrder.verify(queueDomainService).updateSiteQueueSettings(200);
        inOrder.verify(queueDomainService).getSiteQueueSnapshot();
    }

    @Test
    void updateSiteQueueSettings_throwsWhenAdminTokenIsNull() {
        assertThatThrownBy(() -> service.updateSiteQueueSettings(null, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSiteQueueSettings_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.updateSiteQueueSettings(ADMIN_TOKEN, 100))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void updateSiteQueueSettings_throwsWhenNotAdmin() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.updateSiteQueueSettings(ADMIN_TOKEN, 100))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateSiteQueueSettings_throwsWhenMaxVisitorsIsZero() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        assertThatThrownBy(() -> service.updateSiteQueueSettings(ADMIN_TOKEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSiteQueueSettings_throwsWhenMaxVisitorsIsNegative() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        assertThatThrownBy(() -> service.updateSiteQueueSettings(ADMIN_TOKEN, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
