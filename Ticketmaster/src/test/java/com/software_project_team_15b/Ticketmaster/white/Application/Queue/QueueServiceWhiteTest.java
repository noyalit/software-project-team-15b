package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
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
}
