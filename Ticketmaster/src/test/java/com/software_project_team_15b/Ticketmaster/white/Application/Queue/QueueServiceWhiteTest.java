package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * White-box tests for the {@link QueueService} application facade.
 *
 * <p>These tests verify the internal wiring of the facade:
 * <ul>
 *   <li>Token validation via {@link IAuth} occurs before any event-queue domain service
 *       call, and invalid or null tokens short-circuit execution.</li>
 *   <li>Site-queue operations ({@code addUserToSiteQueue}) are managed locally within
 *       this service and never delegated to {@link IQueueDomainService}.</li>
 *   <li>Event-queue operations ({@code getQueueAccessView}, {@code pushToEventQueue})
 *       are forwarded to the domain service after token validation, without mutating
 *       or substituting arguments.</li>
 *   <li>{@code createEventQueue} and {@code deleteEventQueue} delegate directly without
 *       token validation.</li>
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
    // Site-queue methods — managed locally, NOT delegated to domain service
    // =========================================================================

    @Test
    void addUserToSiteQueue_validatesToken_andNeverCallsDomainService() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        service.addUserToSiteQueue("token-a");

        verifyNoInteractions(queueDomainService);
    }

    // =========================================================================
    // Event-queue methods — validate token, then delegate to domain service
    // =========================================================================

    @Test
    void getQueueAccessView_validatesToken_thenDelegates() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid("token-a");
        inOrder.verify(queueDomainService).getQueueAccessView("token-a", EVENT_ID);
        verify(auth).extractUserId("token-a");
    }

    @Test
    void pushToEventQueue_validatesToken_thenDelegates() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        service.pushToEventQueue(EVENT_ID, "token-a");

        var inOrder = inOrder(auth, queueDomainService);
        inOrder.verify(auth).isTokenValid("token-a");
        inOrder.verify(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");
        verify(auth).extractUserId("token-a");
    }

    // =========================================================================
    // Methods that delegate without token validation
    // =========================================================================

    @Test
    void createEventQueue_delegates_andDoesNothingElse() {
        when(userDomainService.isActiveManager(USER_ID, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.createEventQueue(USER_ID, COMPANY_ID, EVENT_ID);

        verify(userDomainService).isActiveManager(USER_ID, COMPANY_ID, EVENT_ID);
        verify(queueDomainService).createEventQueue(EVENT_ID);
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
    void addUserToSiteQueue_nullToken_throwsBeforeAuth() {
        assertThatThrownBy(() -> service.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth, queueDomainService);
    }

    @Test
    void addUserToSiteQueue_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.addUserToSiteQueue("bad-token"))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(queueDomainService);
    }

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
    void pushToEventQueue_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        String tok = "token-xyz";
        when(auth.isTokenValid(tok)).thenReturn(true);
        when(auth.extractUserId(tok)).thenReturn(USER_ID);

        service.pushToEventQueue(eid, tok);

        verify(queueDomainService).pushToEventQueue(eid, tok);
    }

    @Test
    void getQueueAccessView_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        String tok = "tok";
        QueueAccessDTO out = new QueueAccessDTO(eid, QueueAccessStatus.NO_QUEUE, null, null);
        when(auth.isTokenValid(tok)).thenReturn(true);
        when(auth.extractUserId(tok)).thenReturn(USER_ID);
        when(queueDomainService.getQueueAccessView(tok, eid)).thenReturn(out);

        assertThat(service.getQueueAccessView(tok, eid)).isSameAs(out);
        verify(queueDomainService).getQueueAccessView(tok, eid);
    }

    // =========================================================================
    // Concurrency — site queue is stateful; concurrent calls must be safe
    // =========================================================================

    @Test
    void concurrentAddUserToSiteQueue_distinctTokens_neverCallsDomainService() throws InterruptedException {
        when(auth.isTokenValid(anyString())).thenReturn(true);
        when(auth.extractUserId(anyString())).thenReturn(USER_ID);

        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger completed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final String tok = "token-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    service.addUserToSiteQueue(tok);
                    completed.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(completed.get()).isEqualTo(threads);
        verifyNoInteractions(queueDomainService);
    }
}