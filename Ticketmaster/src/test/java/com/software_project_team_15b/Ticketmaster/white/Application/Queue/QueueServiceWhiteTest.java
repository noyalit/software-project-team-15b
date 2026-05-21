package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * White-box tests for the {@link QueueService} application facade.
 *
 * <p>These tests verify the wiring contract of the facade: every public method
 * must forward its arguments to the same-named method on the injected
 * {@link IQueueDomainService}, and the facade must not perform any work beyond
 * that delegation. After each test the mock is checked for {@code noMoreInteractions}
 * to guard against accidental side effects.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceWhiteTest {

    @Mock private IQueueDomainService queueDomainService;
    @InjectMocks private QueueService service;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @AfterEach
    void verifyNoUnexpectedInteractions() {
        verifyNoMoreInteractions(queueDomainService);
    }

    // =========================================================================
    // Single-method delegation tests — exactly one call, no extras
    // =========================================================================

    @Test
    void addUserToSiteQueue_delegates_andDoesNothingElse() {
        service.addUserToSiteQueue("token-a");

        verify(queueDomainService).addUserToSiteQueue("token-a");
    }

    @Test
    void validateAndExitQueue_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.validateAndExitQueue("token-a")).thenReturn(true);

        boolean result = service.validateAndExitQueue("token-a");

        assertThat(result).isTrue();
        verify(queueDomainService).validateAndExitQueue("token-a");
    }

    @Test
    void canAccessWebsite_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.canAccessWebsite()).thenReturn(true);

        boolean result = service.canAccessWebsite();

        assertThat(result).isTrue();
        verify(queueDomainService).canAccessWebsite();
    }

    @Test
    void getPositionInEventQueue_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.getPositionInEventQueue("token-a", EVENT_ID)).thenReturn(3);

        int result = service.getPositionInEventQueue("token-a", EVENT_ID);

        assertThat(result).isEqualTo(3);
        verify(queueDomainService).getPositionInEventQueue("token-a", EVENT_ID);
    }

    @Test
    void isUserAdmitted_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.isUserAdmitted(USER_ID, EVENT_ID)).thenReturn(true);

        boolean result = service.isUserAdmitted(USER_ID, EVENT_ID);

        assertThat(result).isTrue();
        verify(queueDomainService).isUserAdmitted(USER_ID, EVENT_ID);
    }

    @Test
    void getQueueAccessView_delegates_andReturnsDomainServiceResult() {
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(queueDomainService).getQueueAccessView("token-a", EVENT_ID);
    }

    @Test
    void requestAccess_delegates_andReturnsDomainServiceResult() {
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.requestAccess("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.requestAccess("token-a", EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(queueDomainService).requestAccess("token-a", EVENT_ID);
    }

    @Test
    void hasAccess_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(true);

        boolean result = service.hasAccess("token-a", EVENT_ID);

        assertThat(result).isTrue();
        verify(queueDomainService).hasAccess("token-a", EVENT_ID);
    }

    @Test
    void createEventQueue_delegates_andDoesNothingElse() {
        service.createEventQueue(EVENT_ID);
        verify(queueDomainService).createEventQueue(EVENT_ID);
    }

    @Test
    void deleteEventQueue_delegates_andDoesNothingElse() {
        service.deleteEventQueue(EVENT_ID);
        verify(queueDomainService).deleteEventQueue(EVENT_ID);
    }

    @Test
    void popFromEventQueue_delegates_andReturnsDomainServiceResult() {
        when(queueDomainService.popFromEventQueue(EVENT_ID)).thenReturn("token-a");

        String result = service.popFromEventQueue(EVENT_ID);

        assertThat(result).isEqualTo("token-a");
        verify(queueDomainService).popFromEventQueue(EVENT_ID);
    }

    @Test
    void pushToEventQueue_delegates_andDoesNothingElse() {
        service.pushToEventQueue(EVENT_ID, "token-a");
        verify(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");
    }

    // =========================================================================
    // Exception propagation — facade does NOT swallow or wrap exceptions
    // =========================================================================

    @Test
    void addUserToSiteQueue_propagatesDomainServiceException() {
        doThrow(new IllegalArgumentException("boom")).when(queueDomainService).addUserToSiteQueue("token-a");

        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
        verify(queueDomainService).addUserToSiteQueue("token-a");
    }

    @Test
    void requestAccess_propagatesDomainServiceException() {
        doThrow(new RuntimeException("downstream")).when(queueDomainService).requestAccess(anyString(), any());

        assertThatThrownBy(() -> service.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("downstream");
        verify(queueDomainService).requestAccess("token-a", EVENT_ID);
    }

    @Test
    void popFromEventQueue_propagatesDomainServiceException() {
        doThrow(new RuntimeException("empty")).when(queueDomainService).popFromEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("empty");
        verify(queueDomainService).popFromEventQueue(EVENT_ID);
    }

    // =========================================================================
    // Argument pass-through — verify the facade does not mutate or substitute args
    // =========================================================================

    @Test
    void pushToEventQueue_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        String tok = "token-xyz";

        service.pushToEventQueue(eid, tok);

        verify(queueDomainService).pushToEventQueue(eid, tok);
    }

    @Test
    void getQueueAccessView_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        QueueAccessDTO out = new QueueAccessDTO(eid, QueueAccessStatus.NO_QUEUE, null, null);
        when(queueDomainService.getQueueAccessView("tok", eid)).thenReturn(out);

        assertThat(service.getQueueAccessView("tok", eid)).isSameAs(out);
        verify(queueDomainService).getQueueAccessView("tok", eid);
    }

    // =========================================================================
    // Concurrency — facade is stateless; concurrent delegation must not corrupt counts
    // =========================================================================

    @Test
    void concurrentDelegation_eachCallForwardsExactlyOnce() throws InterruptedException {
        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger completed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addUserToSiteQueue("token-x");
                    completed.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(completed.get()).isEqualTo(threads);
        verify(queueDomainService, times(threads)).addUserToSiteQueue("token-x");
    }
}
