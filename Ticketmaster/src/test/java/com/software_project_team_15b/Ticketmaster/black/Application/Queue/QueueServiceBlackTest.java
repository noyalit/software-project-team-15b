package com.software_project_team_15b.Ticketmaster.black.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
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
 * <p>Site-queue admission ({@code acceptUsersFromSiteQueue}) is a scheduled internal
 * method that coordinates auth-based eviction with the domain service; it is not
 * directly invokable through the public API and is therefore not tested here.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceBlackTest {

    @Mock private IQueueDomainService queueDomainService;
    @Mock private IAuth auth;
    @Mock private UserDomainService userDomainService;
    @InjectMocks private QueueService service;

    private static final UUID EVENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    // =========================================================================
    // Event-queue CRUD — positive
    // =========================================================================

    @Test
    void createEventQueue_positive_returnsNormally() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        assertThatCode(() -> service.createEventQueue(USER_ID, COMPANY_ID, EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deleteEventQueue_positive_returnsNormally() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        assertThatCode(() -> service.deleteEventQueue(USER_ID, COMPANY_ID, EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void pushToEventQueue_positive_returnsNormally() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        assertThatCode(() -> service.pushToEventQueue(EVENT_ID, "token-a")).doesNotThrowAnyException();
    }

    // =========================================================================
    // Event-queue CRUD — negative
    // =========================================================================

    @Test
    void createEventQueue_negative_propagatesIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(USER_ID, COMPANY_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_negative_userNotAuthorized_throwsUnauthorized() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_ID, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventQueue(USER_ID, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deleteEventQueue_negative_propagatesQueueNotFound() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).deleteEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventQueue(USER_ID, COMPANY_ID, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void deleteEventQueue_negative_userNotAuthorized_throwsUnauthorized() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_ID, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventQueue(USER_ID, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void pushToEventQueue_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "bad"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void pushToEventQueue_negative_propagatesQueueIsFull() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        doThrow(new QueueIsFullException("full"))
                .when(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueIsFullException.class);
    }

    @Test
    void pushToEventQueue_negative_propagatesAlreadyInQueue() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        doThrow(new AlreadyInQueueException("dup"))
                .when(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(AlreadyInQueueException.class);
    }

    // =========================================================================
    // getQueueAccessView
    // =========================================================================

    @Test
    void getQueueAccessView_positive_returnsNoQueueDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.NO_QUEUE, null, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(result.position()).isNull();
        assertThat(result.accessExpiresAt()).isNull();
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsAdmittedDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(result.accessExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsWaitingDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 5, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(result.position()).isEqualTo(5);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getQueueAccessView_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueAccessView("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

}