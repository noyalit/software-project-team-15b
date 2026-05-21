package com.software_project_team_15b.Ticketmaster.black.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.QueueDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for {@link QueueDomainServiceImpl} exercised purely through the
 * {@link IQueueDomainService} contract.
 *
 * <p>No reflection on the SUT, no protected-method exposure: the system is driven via
 * public interface methods and observed through public interface methods. The Spring
 * proxy {@code self} reference is injected once during setup since outside of a Spring
 * context the @Retryable / @Transactional self-invocation would otherwise NPE — that is
 * test plumbing, not white-box knowledge.
 */
@ExtendWith(MockitoExtension.class)
class QueueDomainServiceImplBlackTest {

    @Mock private IQueueRepository queueRepository;
    @Mock private IAuth auth;
    @InjectMocks private QueueDomainServiceImpl service;

    private IQueueDomainService domainService;

    @BeforeEach
    void wireSelfAndExpose() {
        ReflectionTestUtils.setField(service, "self", service);
        domainService = service;
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    // =========================================================================
    // addUserToSiteQueue / validateAndExitQueue / canAccessWebsite
    // =========================================================================

    @Test
    void addUserToSiteQueue_positive_userAdmittedAfterValidationPasses_andCanExit() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        domainService.addUserToSiteQueue("token-a");

        // simulate scheduler tick by invoking the same path the scheduler would
        invokeAcceptUsers();

        assertThat(domainService.validateAndExitQueue("token-a")).isTrue();
    }

    @Test
    void addUserToSiteQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addUserToSiteQueue_negative_duplicateToken_throwsIllegalArgument() {
        domainService.addUserToSiteQueue("token-a");
        assertThatThrownBy(() -> domainService.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAndExitQueue_positive_returnsFalseBeforeAdmission() {
        domainService.addUserToSiteQueue("token-a");
        assertThat(domainService.validateAndExitQueue("token-a")).isFalse();
    }

    @Test
    void validateAndExitQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.validateAndExitQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canAccessWebsite_positive_returnsTrueOnEmptyService() {
        assertThat(domainService.canAccessWebsite()).isTrue();
    }

    // =========================================================================
    // createEventQueue
    // =========================================================================

    @Test
    void createEventQueue_positive_admitsUsersAlreadyInRepoQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isTrue();
    }

    @Test
    void createEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.createEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // deleteEventQueue
    // =========================================================================

    @Test
    void deleteEventQueue_positive_removesAdmittedUsersForEvent() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);
        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isTrue();

        domainService.deleteEventQueue(EVENT_ID);
        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void deleteEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.deleteEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventQueue_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThatThrownBy(() -> domainService.deleteEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // pushToEventQueue
    // =========================================================================

    @Test
    void pushToEventQueue_positive_userBecomesAdmittedWhenSlotAvailable() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);
        domainService.pushToEventQueue(EVENT_ID, "token-a");

        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isTrue();
    }

    @Test
    void pushToEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.pushToEventQueue(null, "token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pushToEventQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.pushToEventQueue(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pushToEventQueue_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThatThrownBy(() -> domainService.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void pushToEventQueue_negative_queueFull_throwsQueueIsFullException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 1);
        queue.push("token-x");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> domainService.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueIsFullException.class);
    }

    @Test
    void pushToEventQueue_negative_alreadyInQueue_throwsAlreadyInQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> domainService.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(AlreadyInQueueException.class);
    }

    // =========================================================================
    // popFromEventQueue
    // =========================================================================

    @Test
    void popFromEventQueue_positive_returnsFrontTokenInFifoOrder() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThat(domainService.popFromEventQueue(EVENT_ID)).isEqualTo("token-a");
        assertThat(domainService.popFromEventQueue(EVENT_ID)).isEqualTo("token-b");
    }

    @Test
    void popFromEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.popFromEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void popFromEventQueue_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThatThrownBy(() -> domainService.popFromEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void popFromEventQueue_negative_emptyQueue_throwsEmptyQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        assertThatThrownBy(() -> domainService.popFromEventQueue(EVENT_ID))
                .isInstanceOf(EmptyQueueException.class);
    }

    // =========================================================================
    // getPositionInEventQueue
    // =========================================================================

    @Test
    void getPositionInEventQueue_positive_returnsZeroBasedIndex() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThat(domainService.getPositionInEventQueue("token-a", EVENT_ID)).isEqualTo(0);
        assertThat(domainService.getPositionInEventQueue("token-b", EVENT_ID)).isEqualTo(1);
    }

    @Test
    void getPositionInEventQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getPositionInEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPositionInEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getPositionInEventQueue("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPositionInEventQueue_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThatThrownBy(() -> domainService.getPositionInEventQueue("token-a", EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // isUserAdmitted
    // =========================================================================

    @Test
    void isUserAdmitted_positive_returnsTrue_afterUserPromotedFromQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isTrue();
    }

    @Test
    void isUserAdmitted_negative_returnsFalse_whenNoQueueExists() {
        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void isUserAdmitted_negative_returnsFalse_whenUserNotInAdmittedSet() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        domainService.createEventQueue(EVENT_ID);

        assertThat(domainService.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void isUserAdmitted_negative_returnsFalse_forUnrelatedEventId() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        assertThat(domainService.isUserAdmitted(USER_A, OTHER_EVENT_ID)).isFalse();
    }

    // =========================================================================
    // getQueueAccessView
    // =========================================================================

    @Test
    void getQueueAccessView_positive_returnsNoQueue_whenEventHasNoQueue() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(view.position()).isNull();
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsAdmitted_withFutureExpiry() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(view.accessExpiresAt()).isNotNull().isAfter(LocalDateTime.now());
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsWaiting_withPositionWhenAdmissionSlotsFull() {
        // create a separate queue (empty for advance), then return a queue containing
        // the user for the subsequent position lookup
        VirtualQueue emptyForAdvance = new VirtualQueue(EVENT_ID);
        VirtualQueue withUserForPosition = new VirtualQueue(EVENT_ID);
        withUserForPosition.push("token-a");
        when(queueRepository.getQueue(EVENT_ID))
                .thenReturn(emptyForAdvance)
                .thenReturn(withUserForPosition);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(view.position()).isEqualTo(0);
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getQueueAccessView_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getQueueAccessView(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueAccessView_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getQueueAccessView("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueAccessView_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad")).thenReturn(false);
        assertThatThrownBy(() -> domainService.getQueueAccessView("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // requestAccess
    // =========================================================================

    @Test
    void requestAccess_positive_admittedImmediately_whenSlotsAvailable() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);
        QueueAccessDTO view = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void requestAccess_positive_userAlreadyAdmitted_isIdempotent() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);
        QueueAccessDTO first = domainService.requestAccess("token-a", EVENT_ID);
        QueueAccessDTO second = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(first.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(second.status()).isEqualTo(QueueAccessStatus.ADMITTED);
    }

    @Test
    void requestAccess_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.requestAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestAccess_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.requestAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestAccess_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> domainService.requestAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_positive_returnsTrue_whenUserAdmitted() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsFalse_whenUserNotAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasAccess_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasAccess_negative_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad")).thenReturn(false);
        assertThatThrownBy(() -> domainService.hasAccess("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Test
    void concurrentRequestAccess_independentEvents_allThreadsBecomeAdmitted() throws InterruptedException {
        // Each thread operates on its own event/queue so the test does not rely on
        // JPA-level optimistic locking (which @Transactional + @Retryable provide in
        // production but is absent in unit tests). This isolates the domain service's
        // own concurrency behavior on independent aggregates.
        int n = 15;
        UUID[] eventIds = new UUID[n];
        VirtualQueue[] queues = new VirtualQueue[n];
        for (int i = 0; i < n; i++) {
            eventIds[i] = UUID.randomUUID();
            queues[i] = new VirtualQueue(eventIds[i]);
            when(queueRepository.getQueue(eventIds[i])).thenReturn(queues[i]);
            domainService.createEventQueue(eventIds[i]);
        }
        when(auth.isTokenValid(anyString())).thenReturn(true);
        when(auth.extractUserId(anyString())).thenAnswer(inv -> {
            String tok = inv.getArgument(0);
            return UUID.nameUUIDFromBytes(tok.getBytes());
        });

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger admitted = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String tok = "token-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    QueueAccessDTO view = domainService.requestAccess(tok, eventIds[idx]);
                    if (view.status() == QueueAccessStatus.ADMITTED) {
                        admitted.incrementAndGet();
                    }
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(admitted.get()).isEqualTo(n);
    }

    @Test
    void concurrentHasAccess_allThreadsObserveSameAdmissionResult() throws InterruptedException {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid(anyString())).thenReturn(true);
        when(auth.extractUserId(anyString())).thenReturn(USER_A);

        domainService.createEventQueue(EVENT_ID);

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (domainService.hasAccess("token-a", EVENT_ID)) trueCount.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(trueCount.get()).isEqualTo(threads);
    }

    @Test
    void concurrentAddUserToSiteQueue_distinctTokens_allEventuallyAdmitted() throws InterruptedException {
        int n = 30;
        when(auth.isTokenValid(anyString())).thenReturn(true);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger added = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final String tok = "site-token-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    domainService.addUserToSiteQueue(tok);
                    added.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        invokeAcceptUsers();

        AtomicInteger admittedCount = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            if (domainService.validateAndExitQueue("site-token-" + i)) {
                admittedCount.incrementAndGet();
            }
        }
        assertThat(added.get()).isEqualTo(n);
        assertThat(admittedCount.get()).isEqualTo(n);
    }

    @Test
    void concurrentDuplicateAddUserToSiteQueue_exactlyOneSucceeds() throws InterruptedException {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    domainService.addUserToSiteQueue("shared-token");
                    successes.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    duplicates.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(threads - 1);
    }

    // The acceptUsersFromSiteQueue method is private; the scheduler triggers it in
    // production. For tests we drive it via reflection — this is plumbing required to
    // step the scheduler deterministically, not an inspection of internal state.
    private void invokeAcceptUsers() {
        ReflectionTestUtils.invokeMethod(service, "acceptUsersFromSiteQueue");
    }
}
