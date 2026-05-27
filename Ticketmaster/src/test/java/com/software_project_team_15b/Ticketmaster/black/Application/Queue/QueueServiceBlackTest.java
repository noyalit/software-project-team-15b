package com.software_project_team_15b.Ticketmaster.black.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for the {@link QueueService} application facade.
 *
 * <p>The facade is exercised purely through its public API and observed through
 * return values and propagated exceptions. The underlying {@link IQueueDomainService}
 * is stubbed to drive each scenario; tests do not verify call ordering, count, or
 * argument forwarding — those concerns belong to the white-box suite.
 *
 * <p>All mutating operations ({@code createEventQueue}, {@code deleteEventQueue},
 * {@code clearEventQueue}, {@code updateEventQueueSettings}) and admin reads
 * ({@code getQueueSnapshot}, {@code getAllQueueSnapshots}) require the caller to be
 * a system admin as determined by {@link IAuth#isSystemAdmin}.
 *
 * <p>Site-queue admission ({@code acceptUsersFromSiteQueue}) is a scheduled internal
 * method that coordinates auth-based eviction with the domain service; it is not
 * directly invokable through the public API and is therefore not tested here.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceBlackTest {

    @Mock private IQueueDomainService queueDomainService;
    @Mock private IAuth auth;
    @InjectMocks private QueueService service;

    private static final UUID   EVENT_ID        = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_TOKEN     = "admin-token";
    private static final String NON_ADMIN_TOKEN = "non-admin-token";

    // =========================================================================
    // createEventQueue — positive
    // =========================================================================

    /**
     * A system admin with valid arguments should be able to create an event queue
     * without any exception being thrown.
     */
    @Test
    void createEventQueue_positive_returnsNormally() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        assertThatCode(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 1000, 100))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // createEventQueue — negative
    // =========================================================================

    /**
     * A null token must be rejected before any auth or domain call is made.
     */
    @Test
    void createEventQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(null, EVENT_ID, 1000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A null eventId must be rejected before any auth or domain call is made.
     */
    @Test
    void createEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, null, 1000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A negative capacity must be rejected with an {@link IllegalArgumentException}.
     * Zero is permitted for creation (the queue can be expanded later via update).
     */
    @Test
    void createEventQueue_negative_negativeCapacity_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A negative max-accepted value must be rejected with an {@link IllegalArgumentException}.
     * Zero is permitted for creation (the limit can be set later via update).
     */
    @Test
    void createEventQueue_negative_negativeMaxAccepted_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(ADMIN_TOKEN, EVENT_ID, 1000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void createEventQueue_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventQueue(NON_ADMIN_TOKEN, EVENT_ID, 1000, 100))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}.
     */
    @Test
    void createEventQueue_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventQueue(NON_ADMIN_TOKEN, EVENT_ID, 1000, 100))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // deleteEventQueue — positive
    // =========================================================================

    /**
     * A system admin with a valid eventId should be able to delete an event queue
     * without any exception being thrown.
     */
    @Test
    void deleteEventQueue_positive_returnsNormally() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        assertThatCode(() -> service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // deleteEventQueue — negative
    // =========================================================================

    /**
     * A {@link QueueNotFoundException} thrown by the domain service must propagate
     * to the caller unchanged.
     */
    @Test
    void deleteEventQueue_negative_propagatesQueueNotFound() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).deleteEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void deleteEventQueue_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventQueue(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}
     * when attempting to delete an event queue.
     */
    @Test
    void deleteEventQueue_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventQueue(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // clearEventQueue — positive
    // =========================================================================

    /**
     * A system admin with a valid eventId should be able to clear an event queue
     * without any exception being thrown.
     */
    @Test
    void clearEventQueue_positive_returnsNormally() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        assertThatCode(() -> service.clearEventQueue(ADMIN_TOKEN, EVENT_ID))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // clearEventQueue — negative
    // =========================================================================

    /**
     * A null token must be rejected before any auth or domain call is made.
     */
    @Test
    void clearEventQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.clearEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A null eventId must be rejected before any auth or domain call is made.
     */
    @Test
    void clearEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.clearEventQueue(ADMIN_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void clearEventQueue_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.clearEventQueue(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}.
     */
    @Test
    void clearEventQueue_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.clearEventQueue(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * A {@link QueueNotFoundException} thrown by the domain service must propagate
     * to the caller unchanged.
     */
    @Test
    void clearEventQueue_negative_propagatesQueueNotFound() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).clearEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.clearEventQueue(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getQueueSnapshot — positive
    // =========================================================================

    /**
     * A system admin should receive the {@link QueueSnapshotDTO} returned by the
     * domain service, with all fields preserved.
     */
    @Test
    void getQueueSnapshot_positive_returnsSnapshot() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        QueueSnapshotDTO expected = new QueueSnapshotDTO(EVENT_ID, 500, 50, 12, 8, Map.of());
        when(queueDomainService.getQueueSnapshot(EVENT_ID)).thenReturn(expected);

        QueueSnapshotDTO result = service.getQueueSnapshot(ADMIN_TOKEN, EVENT_ID);

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.capacity()).isEqualTo(500);
        assertThat(result.maxAccepted()).isEqualTo(50);
        assertThat(result.waitingCount()).isEqualTo(12);
        assertThat(result.admittedCount()).isEqualTo(8);
    }

    // =========================================================================
    // getQueueSnapshot — negative
    // =========================================================================

    /**
     * A null token must be rejected before any auth or domain call is made.
     */
    @Test
    void getQueueSnapshot_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueSnapshot(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A null eventId must be rejected before any auth or domain call is made.
     */
    @Test
    void getQueueSnapshot_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueSnapshot(ADMIN_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void getQueueSnapshot_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueSnapshot(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}.
     */
    @Test
    void getQueueSnapshot_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueSnapshot(NON_ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * A {@link QueueNotFoundException} thrown by the domain service must propagate
     * to the caller unchanged.
     */
    @Test
    void getQueueSnapshot_negative_propagatesQueueNotFound() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).getQueueSnapshot(EVENT_ID);

        assertThatThrownBy(() -> service.getQueueSnapshot(ADMIN_TOKEN, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // updateEventQueueSettings — positive
    // =========================================================================

    /**
     * A system admin with valid arguments should be able to update queue settings
     * without any exception being thrown.
     */
    @Test
    void updateEventQueueSettings_positive_returnsNormally() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);

        assertThatCode(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 200, 20))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // updateEventQueueSettings — negative
    // =========================================================================

    /**
     * A null eventId must be rejected before any auth or domain call is made.
     */
    @Test
    void updateEventQueueSettings_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, null, 200, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A non-positive capacity must be rejected with an {@link IllegalArgumentException}.
     */
    @Test
    void updateEventQueueSettings_negative_negativeCapacity_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A non-positive max-accepted value must be rejected with an {@link IllegalArgumentException}.
     */
    @Test
    void updateEventQueueSettings_negative_negativeMaxAccepted_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 200, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void updateEventQueueSettings_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.updateEventQueueSettings(NON_ADMIN_TOKEN, EVENT_ID, 200, 20))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}.
     */
    @Test
    void updateEventQueueSettings_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.updateEventQueueSettings(NON_ADMIN_TOKEN, EVENT_ID, 200, 20))
                .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * A {@link QueueNotFoundException} thrown by the domain service must propagate
     * to the caller unchanged.
     */
    @Test
    void updateEventQueueSettings_negative_propagatesQueueNotFound() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).updateQueueSettings(EVENT_ID, 200, 20);

        assertThatThrownBy(() -> service.updateEventQueueSettings(ADMIN_TOKEN, EVENT_ID, 200, 20))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getAllQueueSnapshots — positive
    // =========================================================================

    /**
     * A system admin should receive the list of {@link QueueSnapshotDTO} objects
     * returned by the domain service.
     */
    @Test
    void getAllQueueSnapshots_positive_returnsAllSnapshots() {
        when(auth.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(ADMIN_TOKEN)).thenReturn(true);
        QueueSnapshotDTO snap = new QueueSnapshotDTO(EVENT_ID, 100, 10, 3, 2, Map.of());
        when(queueDomainService.getAllQueueSnapshots()).thenReturn(List.of(snap));

        List<QueueSnapshotDTO> result = service.getAllQueueSnapshots(ADMIN_TOKEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo(EVENT_ID);
    }

    // =========================================================================
    // getAllQueueSnapshots — negative
    // =========================================================================

    /**
     * A null token must be rejected before any auth or domain call is made.
     */
    @Test
    void getAllQueueSnapshots_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAllQueueSnapshots(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * An invalid or expired token must cause an {@link InvalidTokenException} before the
     * admin check is reached.
     */
    @Test
    void getAllQueueSnapshots_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.getAllQueueSnapshots(NON_ADMIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);
    }

    /**
     * A caller who is not a system admin must receive an {@link UnauthorizedException}.
     */
    @Test
    void getAllQueueSnapshots_negative_notAdmin_throwsUnauthorized() {
        when(auth.isTokenValid(NON_ADMIN_TOKEN)).thenReturn(true);
        when(auth.isSystemAdmin(NON_ADMIN_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.getAllQueueSnapshots(NON_ADMIN_TOKEN))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // getQueueAccessView — positive
    // =========================================================================

    /**
     * When the event has no queue the domain service returns a {@code NO_QUEUE} DTO;
     * the facade must return it unchanged with {@code canCreateActiveOrder() == true}.
     */
    @Test
    void getQueueAccessView_positive_returnsNoQueueDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.NO_QUEUE, null, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(result.position()).isNull();
        assertThat(result.accessExpiresAt()).isNull();
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    /**
     * When the user has been admitted, the facade returns a DTO whose expiry is in
     * the future and {@code canCreateActiveOrder() == true}.
     */
    @Test
    void getQueueAccessView_positive_returnsAdmittedDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(result.accessExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    /**
     * When the user is still waiting, the facade returns a DTO with the correct
     * position and {@code canCreateActiveOrder() == false}.
     */
    @Test
    void getQueueAccessView_positive_returnsWaitingDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 5, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(result.position()).isEqualTo(5);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    // =========================================================================
    // getQueueAccessView — negative
    // =========================================================================

    /**
     * An invalid token must cause an {@link InvalidTokenException} to be thrown
     * before any domain-service call is made.
     */
    @Test
    void getQueueAccessView_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueAccessView("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }
}
