package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * White-box tests for the {@link QueueService} application facade.
 *
 * <p>These tests verify the internal wiring of the facade:
 * <ul>
 *   <li>Token validation via {@link IAuth} occurs before any event-queue domain service
 *       call, and invalid or null tokens short-circuit execution.</li>
 *   <li>{@code getQueueAccessView} is forwarded to the domain service after token
 *       validation, without mutating or substituting arguments.</li>
 *   <li>{@code createEventQueue} and {@code deleteEventQueue} check authorization via
 *       {@link com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService}
 *       then delegate to the domain service without touching the auth token.</li>
 * </ul>
 *
 * <p>After each test, {@code verifyNoMoreInteractions} on the domain service mock guards
 * against accidental extra delegations.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceWhiteTest {

    @Mock private IQueueDomainService queueDomainService;
    @Mock private IAuth auth;
    @Mock private UserDomainService userDomainService;
    @InjectMocks private QueueService service;

    private static final UUID EVENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @AfterEach
    void verifyNoUnexpectedDomainServiceInteractions() {
        verifyNoMoreInteractions(queueDomainService, userDomainService);
    }

    // =========================================================================
    // Event-queue methods — validate token, then delegate to domain service
    // =========================================================================

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

    // =========================================================================
    // Methods that delegate without token validation
    // =========================================================================

    @Test
    void createEventQueue_delegates_andDoesNothingElse() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.createEventQueue(USER_ID, COMPANY_ID, EVENT_ID, 1000, 100);

        verify(userDomainService).isActiveManager(USER_ID, COMPANY_ID, EVENT_ID);
        verify(queueDomainService).createEventQueue(EVENT_ID, 1000, 100);
        verifyNoInteractions(auth);
    }

    @Test
    void deleteEventQueue_delegates_andDoesNothingElse() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.deleteEventQueue(USER_ID, COMPANY_ID, EVENT_ID);

        verify(userDomainService).isActiveManager(USER_ID, COMPANY_ID, EVENT_ID);
        verify(queueDomainService).deleteEventQueue(EVENT_ID);
        verifyNoInteractions(auth);
    }

    // =========================================================================
    // Exception propagation — service does NOT swallow or wrap exceptions
    // =========================================================================

    @Test
    void deleteEventQueue_propagatesDomainServiceException() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new QueueNotFoundException("missing")).when(queueDomainService).deleteEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventQueue(USER_ID, COMPANY_ID, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
        verify(userDomainService).isActiveManager(USER_ID, COMPANY_ID, EVENT_ID);
        verify(queueDomainService).deleteEventQueue(EVENT_ID);
        verifyNoInteractions(auth);
    }

    // =========================================================================
    // Argument pass-through — verify args are not mutated or substituted
    // =========================================================================

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

}