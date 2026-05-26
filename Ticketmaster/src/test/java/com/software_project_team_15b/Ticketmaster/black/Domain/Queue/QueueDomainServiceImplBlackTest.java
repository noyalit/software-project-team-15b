package com.software_project_team_15b.Ticketmaster.black.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for {@link QueueDomainServiceImpl} exercised purely through the
 * {@link IQueueDomainService} contract.
 *
 * <p>No reflection on the SUT. The system is driven via public interface methods and
 * observed through public interface methods only. Where admission must be triggered
 * deterministically (bypassing the {@code @Scheduled} boundary), an
 * {@link ExposedQueuesService} subclass exposes {@code advanceEventQueues}.
 *
 * <p>Admission now happens exclusively inside {@code advanceEventQueues}: users pushed to
 * a queue's waiting list are promoted to {@code accessMap} only when that method runs.
 */
@ExtendWith(MockitoExtension.class)
class QueueDomainServiceImplBlackTest {

    @Mock private IQueueRepository queueRepository;
    @InjectMocks private QueueDomainServiceImpl service;

    private IQueueDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = service;
    }

    private static final UUID EVENT_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    /** Exposes the protected {@code advanceEventQueues} for deterministic scheduling in tests. */
    private static class ExposedQueuesService extends QueueDomainServiceImpl {
        ExposedQueuesService(IQueueRepository r) { super(r); }
        public void advanceAll() { advanceEventQueues(); }
    }

    private ExposedQueuesService createExposed() {
        return new ExposedQueuesService(queueRepository);
    }

    /** Builds a VirtualQueue with the given token already admitted (in accessMap). */
    private static VirtualQueue admittedQueue(UUID id, String token) {
        VirtualQueue q = new VirtualQueue(id, Integer.MAX_VALUE, 100);
        q.push(token);
        q.advanceQueue(LocalDateTime.now().plusSeconds(100));
        return q;
    }

    // =========================================================================
    // Site-queue operations
    // =========================================================================

    @Test
    void addUserToSiteQueue_positive_addsTokenSuccessfully() {
        assertThatCode(() -> domainService.addUserToSiteQueue("token-a"))
                .doesNotThrowAnyException();
    }

    @Test
    void addUserToSiteQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addUserToSiteQueue_negative_duplicate_throwsIllegalArgument() {
        domainService.addUserToSiteQueue("token-a");
        assertThatThrownBy(() -> domainService.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAcceptedTokens_positive_returnsAdmittedTokensAfterAccept() {
        domainService.addUserToSiteQueue("token-a");
        domainService.acceptUsersFromSiteQueue();
        assertThat(domainService.getAcceptedTokens()).contains("token-a");
    }

    @Test
    void acceptUsersFromSiteQueue_positive_movesWaitingTokenIntoAdmittedSet() {
        domainService.addUserToSiteQueue("token-a");
        domainService.acceptUsersFromSiteQueue();
        assertThat(domainService.getAcceptedTokens()).contains("token-a");
    }

    @Test
    void removeAcceptedToken_positive_removesTokenFromAdmittedSet() {
        domainService.addUserToSiteQueue("token-a");
        domainService.acceptUsersFromSiteQueue();
        domainService.removeAcceptedToken("token-a");
        assertThat(domainService.getAcceptedTokens()).doesNotContain("token-a");
    }

    @Test
    void removeAcceptedToken_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.removeAcceptedToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeAcceptedToken_negative_tokenNotAdmitted_throwsInvalidToken() {
        assertThatThrownBy(() -> domainService.removeAcceptedToken("not-admitted"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void canAccessWebsite_positive_returnsTrueWhenNobodyAdmitted() {
        assertThat(domainService.canAccessWebsite()).isTrue();
    }

    @Test
    void canAccessWebsite_negative_returnsFalseWhenSiteCapReached() {
        for (int i = 0; i < 100; i++) {
            domainService.addUserToSiteQueue("site-tok-" + i);
        }
        domainService.acceptUsersFromSiteQueue();
        assertThat(domainService.canAccessWebsite()).isFalse();
    }

    // =========================================================================
    // createEventQueue
    // =========================================================================

    @Test
    void createEventQueue_positive_createsQueueSuccessfully() {
        assertThatCode(() -> domainService.createEventQueue(EVENT_ID, 1000, 100))
                .doesNotThrowAnyException();
        verify(queueRepository).addQueue(any(VirtualQueue.class));
    }

    @Test
    void createEventQueue_positive_usersAdmittedAfterAdvance() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        queue.push("token-a");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.createEventQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        exposed.advanceAll();

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void createEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.createEventQueue(null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_negative_negativeCapacity_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.createEventQueue(EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventQueue_negative_negativeMaxAccepted_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.createEventQueue(EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // deleteEventQueue
    // =========================================================================

    @Test
    void deleteEventQueue_positive_removesQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        assertThatCode(() -> domainService.deleteEventQueue(EVENT_ID))
                .doesNotThrowAnyException();
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
    void pushToEventQueue_positive_addsTokenToWaitingList() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        domainService.pushToEventQueue(EVENT_ID, "token-a");

        assertThat(queue.contains("token-a")).isTrue();
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
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 1, Integer.MAX_VALUE);
        queue.push("token-x");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> domainService.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueIsFullException.class);
    }

    @Test
    void pushToEventQueue_negative_alreadyInQueue_throwsAlreadyInQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
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
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
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
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        assertThatThrownBy(() -> domainService.popFromEventQueue(EVENT_ID))
                .isInstanceOf(EmptyQueueException.class);
    }

    // =========================================================================
    // getPositionInEventQueue
    // =========================================================================

    @Test
    void getPositionInEventQueue_positive_returnsZeroBasedIndex() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
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
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_positive_returnsTrueWhenUserAdmitted() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(admittedQueue(EVENT_ID, "token-a"));
        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsTrueWhenNoQueueExistsForEvent() {
        // No queue = unrestricted access
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsFalseWhenUserInWaitingQueueButNotAdmitted() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_positive_returnsFalseWhenUserNotPresentAtAll() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_positive_returnsFalseForUnrelatedEventId() {
        when(queueRepository.getQueue(OTHER_EVENT_ID)).thenReturn(
                new VirtualQueue(OTHER_EVENT_ID, Integer.MAX_VALUE, 100));
        assertThat(domainService.hasAccess("token-a", OTHER_EVENT_ID)).isFalse();
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

    // =========================================================================
    // getQueueAccessView
    // =========================================================================

    @Test
    void getQueueAccessView_positive_returnsNoQueue_whenEventHasNoQueue() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(view.position()).isNull();
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsAdmitted_withFutureExpiry() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(admittedQueue(EVENT_ID, "token-a"));

        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(view.accessExpiresAt()).isNotNull().isAfter(LocalDateTime.now());
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsWaiting_whenUserInWaitingList() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        QueueAccessDTO view = domainService.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(view.position()).isEqualTo(0);
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getQueueAccessView_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getQueueAccessView("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // requestAccess
    // =========================================================================

    @Test
    void requestAccess_positive_returnsWaiting_forNewUser() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        QueueAccessDTO view = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(view.position()).isEqualTo(0);
    }

    @Test
    void requestAccess_positive_returnsAdmitted_whenUserAlreadyInAccessMap() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(admittedQueue(EVENT_ID, "token-a"));

        QueueAccessDTO first  = domainService.requestAccess("token-a", EVENT_ID);
        QueueAccessDTO second = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(first.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(second.status()).isEqualTo(QueueAccessStatus.ADMITTED);
    }

    @Test
    void requestAccess_positive_returnsNoQueue_whenNoQueueForEvent() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        QueueAccessDTO view = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void requestAccess_negative_throwsAlreadyInQueue_whenUserAlreadyWaiting() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> domainService.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(AlreadyInQueueException.class);
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

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Test
    void concurrentRequestAccess_independentEvents_allSucceedWithoutExceptions() throws InterruptedException {
        int n = 15;
        UUID[] eventIds = new UUID[n];
        for (int i = 0; i < n; i++) {
            eventIds[i] = UUID.randomUUID();
            VirtualQueue q = new VirtualQueue(eventIds[i], Integer.MAX_VALUE, 0);
            when(queueRepository.getQueue(eventIds[i])).thenReturn(q);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger waiting = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String tok = "token-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    QueueAccessDTO view = domainService.requestAccess(tok, eventIds[idx]);
                    if (view.status() == QueueAccessStatus.WAITING) waiting.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(waiting.get()).isEqualTo(n);
    }

    @Test
    void concurrentRequestAccess_sameEvent_allAdmittedAfterAdvance() throws InterruptedException {
        ExposedQueuesService exposed = createExposed();
        int n = 10;
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, n);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));

        // Push sequentially — VirtualQueue.push() delegates to ArrayList.add(), which is not
        // thread-safe; concurrent pushes to the same queue can silently drop entries.
        for (int i = 0; i < n; i++) {
            exposed.pushToEventQueue(EVENT_ID, "token-" + i);
        }

        // Advance concurrently: after the first thread runs advanceAll() the accessMap is full
        // (future expiry), so all subsequent concurrent calls become safe read-only no-ops.
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                exposed.advanceAll();
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        for (int i = 0; i < n; i++) {
            assertThat(exposed.hasAccess("token-" + i, EVENT_ID)).isTrue();
        }
    }

    @Test
    void concurrentAddUserToSiteQueue_distinctTokens_allSucceed() throws InterruptedException {
        int n = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final String tok = "site-token-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    domainService.addUserToSiteQueue(tok);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(n);
    }

    @Test
    void concurrentHasAccess_allThreadsObserveSameAdmissionResult() throws InterruptedException {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(admittedQueue(EVENT_ID, "token-a"));

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

    // =========================================================================
    // clearEventQueue
    // =========================================================================

    @Test
    void clearEventQueue_positive_removesAllWaitingUsers() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        domainService.clearEventQueue(EVENT_ID);

        assertThat(queue.contains("token-a")).isFalse();
        assertThat(queue.contains("token-b")).isFalse();
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void clearEventQueue_positive_removesAllAdmittedUsers() {
        VirtualQueue queue = admittedQueue(EVENT_ID, "token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        domainService.clearEventQueue(EVENT_ID);

        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void clearEventQueue_positive_emptyQueueIsNoOp() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatCode(() -> domainService.clearEventQueue(EVENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void clearEventQueue_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.clearEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearEventQueue_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.clearEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // updateQueueSettings
    // =========================================================================

    @Test
    void updateQueueSettings_positive_updatesCapacityAndMaxAccepted() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 100, 10);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        domainService.updateQueueSettings(EVENT_ID, 500, 50);

        assertThat(queue.getCapacity()).isEqualTo(500);
        assertThat(queue.getMaxAccepted()).isEqualTo(50);
    }

    @Test
    void updateQueueSettings_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.updateQueueSettings(null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateQueueSettings_negative_negativeCapacity_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.updateQueueSettings(EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateQueueSettings_negative_negativeMaxAccepted_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.updateQueueSettings(EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateQueueSettings_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.updateQueueSettings(EVENT_ID, 100, 10))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getQueueSnapshot
    // =========================================================================

    @Test
    void getQueueSnapshot_positive_reflectsCurrentQueueState() {
        VirtualQueue queue = admittedQueue(EVENT_ID, "token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        QueueSnapshotDTO snapshot = domainService.getQueueSnapshot(EVENT_ID);

        assertThat(snapshot.eventId()).isEqualTo(EVENT_ID);
        assertThat(snapshot.admittedCount()).isEqualTo(1);
        assertThat(snapshot.waitingCount()).isEqualTo(1);
        assertThat(snapshot.admittedUsers()).containsKey("token-a");
    }

    @Test
    void getQueueSnapshot_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getQueueSnapshot(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getQueueSnapshot_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.getQueueSnapshot(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getAllQueueSnapshots
    // =========================================================================

    @Test
    void getAllQueueSnapshots_positive_returnsOneSnapshotPerQueue() {
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        VirtualQueue q1 = new VirtualQueue(EVENT_ID, 500, 50);
        VirtualQueue q2 = new VirtualQueue(id2, 200, 20);
        when(queueRepository.getAllQueues()).thenReturn(List.of(q1, q2));

        List<QueueSnapshotDTO> snapshots = domainService.getAllQueueSnapshots();

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(QueueSnapshotDTO::eventId)
                .containsExactlyInAnyOrder(EVENT_ID, id2);
    }

    @Test
    void getAllQueueSnapshots_positive_returnsEmptyListWhenNoQueues() {
        when(queueRepository.getAllQueues()).thenReturn(List.of());

        assertThat(domainService.getAllQueueSnapshots()).isEmpty();
    }

    @Test
    void getAllQueueSnapshots_positive_snapshotFieldsMatchQueue() {
        VirtualQueue queue = admittedQueue(EVENT_ID, "token-a");
        queue.push("token-b");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));

        QueueSnapshotDTO snap = domainService.getAllQueueSnapshots().get(0);

        assertThat(snap.capacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(snap.maxAccepted()).isEqualTo(100);
        assertThat(snap.admittedCount()).isEqualTo(1);
        assertThat(snap.waitingCount()).isEqualTo(1);
    }
}
