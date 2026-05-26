package com.software_project_team_15b.Ticketmaster.white.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.QueueDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * White-box tests for {@link QueueDomainServiceImpl}.
 *
 * <p>These tests verify repository interaction patterns, internal state transitions, and
 * protected helper behaviour. An {@link ExposedQueuesService} subclass widens
 * {@code advanceEventQueues} so that the scheduler-driven admission path can be exercised
 * deterministically without a Spring context.
 *
 * <p>Admission now lives entirely inside {@code VirtualQueue.accessMap}: users pushed to
 * the waiting list are promoted only when {@code advanceEventQueues} runs.
 */
@ExtendWith(MockitoExtension.class)
class QueueDomainServiceImplWhiteTest {

    @Mock private IQueueRepository queueRepository;
    @InjectMocks private QueueDomainServiceImpl service;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Subclass that widens the protected {@code advanceEventQueues} so tests can
     * deterministically trigger the scheduler-driven admission path.
     */
    private static class ExposedQueuesService extends QueueDomainServiceImpl {
        ExposedQueuesService(IQueueRepository r) { super(r); }
        public void advanceAll() { advanceEventQueues(); }
    }

    private ExposedQueuesService createExposed() {
        return new ExposedQueuesService(queueRepository);
    }

    /** Returns a VirtualQueue with the token already admitted in accessMap. */
    private static VirtualQueue admittedQueue(UUID id, String token) {
        VirtualQueue q = new VirtualQueue(id, Integer.MAX_VALUE, 100);
        q.push(token);
        q.advanceQueue(LocalDateTime.now().plusSeconds(100));
        return q;
    }

    // =========================================================================
    // Site-queue operations — verify-based
    // =========================================================================

    @Test
    void addUserToSiteQueue_positive_succeeds() {
        assertThatCode(() -> service.addUserToSiteQueue("token-a"))
                .doesNotThrowAnyException();
    }

    @Test
    void addUserToSiteQueue_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void addUserToSiteQueue_negative_duplicate_throwsIllegalArgument() {
        service.addUserToSiteQueue("token-a");
        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptUsersFromSiteQueue_positive_movesTokenIntoAcceptedSet() {
        service.addUserToSiteQueue("token-a");
        service.acceptUsersFromSiteQueue();
        assertThat(service.getAcceptedTokens()).contains("token-a");
    }

    @Test
    void removeAcceptedToken_positive_removesFromAcceptedSet() {
        service.addUserToSiteQueue("token-a");
        service.acceptUsersFromSiteQueue();
        service.removeAcceptedToken("token-a");
        assertThat(service.getAcceptedTokens()).doesNotContain("token-a");
    }

    @Test
    void removeAcceptedToken_negative_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.removeAcceptedToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canAccessWebsite_returnsTrueWhenAcceptedSetIsEmpty() {
        assertThat(service.canAccessWebsite()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void canAccessWebsite_returnsFalseWhenAcceptedSetIsAtCap() {
        Set<String> acceptedTokens = (Set<String>) ReflectionTestUtils.getField(service, "acceptedTokens");
        for (int i = 0; i < 100; i++) {
            acceptedTokens.add("tok-" + i);
        }
        assertThat(service.canAccessWebsite()).isFalse();
    }

    // =========================================================================
    // getPositionInEventQueue — verify-based negative
    // =========================================================================

    @Test
    void getPositionInEventQueue_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getPositionInEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getPositionInEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getPositionInEventQueue("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getPositionInEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getPositionInEventQueue("token-a", EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // Queue CRUD — verify-based positive
    // =========================================================================

    @Test
    void createEventQueue_callsAddQueueOnRepository() {
        service.createEventQueue(EVENT_ID, 1000, 100);

        verify(queueRepository).addQueue(any(VirtualQueue.class));
        verifyNoMoreInteractions(queueRepository);
    }

    @Test
    void createEventQueue_doesNotCallGetQueueOnRepository() {
        service.createEventQueue(EVENT_ID, 1000, 100);

        verify(queueRepository, never()).getQueue(any());
    }

    @Test
    void deleteEventQueue_callsRemoveQueueOnRepository() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.deleteEventQueue(EVENT_ID);

        verify(queueRepository).removeQueue(queue);
    }

    @Test
    void pushToEventQueue_addsUserToQueueAndCallsUpdateQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.pushToEventQueue(EVENT_ID, "token-a");

        assertThat(queue.contains("token-a")).isTrue();
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void popFromEventQueue_returnsFrontUserAndCallsUpdateQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        String result = service.popFromEventQueue(EVENT_ID);

        assertThat(result).isEqualTo("token-a");
        verify(queueRepository).updateQueue(queue);
    }

    // =========================================================================
    // Queue CRUD — verify-based negative
    // =========================================================================

    @Test
    void createEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void createEventQueue_negativeCapacity_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void createEventQueue_negativeMaxAccepted_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void deleteEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.deleteEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void deleteEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).removeQueue(any());
    }

    @Test
    void pushToEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.pushToEventQueue(null, "token-a"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void pushToEventQueue_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void pushToEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void pushToEventQueue_queueIsFull_throwsQueueIsFullException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 1, Integer.MAX_VALUE);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-b"))
                .isInstanceOf(QueueIsFullException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void pushToEventQueue_alreadyInQueue_throwsAlreadyInQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(AlreadyInQueueException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void popFromEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.popFromEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void popFromEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void popFromEventQueue_emptyQueue_throwsEmptyQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(EmptyQueueException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    // =========================================================================
    // advanceEventQueues — ExposedQueuesService tests
    // =========================================================================

    @Test
    void advanceEventQueues_callsUpdateQueueForEachQueue() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue q1 = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        VirtualQueue q2 = new VirtualQueue(UUID.randomUUID(), Integer.MAX_VALUE, 100);
        when(queueRepository.getAllQueues()).thenReturn(List.of(q1, q2));

        exposed.advanceAll();

        verify(queueRepository).updateQueue(q1);
        verify(queueRepository).updateQueue(q2);
    }

    @Test
    void advanceEventQueues_promotesWaitingUsersIntoAccessMap() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.advanceAll();

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isTrue();
        assertThat(exposed.hasAccess("token-b", EVENT_ID)).isTrue();
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void advanceEventQueues_setsAccessExpiryToAccessTimeSecondsFromNow() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 100);
        queue.push("token-a");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        LocalDateTime before = LocalDateTime.now();
        exposed.advanceAll();
        LocalDateTime after = LocalDateTime.now();

        QueueAccessDTO view = exposed.getQueueAccessView("token-a", EVENT_ID);
        assertThat(view.accessExpiresAt())
                .isBetween(before.plusSeconds(100), after.plusSeconds(100));
    }

    @Test
    void advanceEventQueues_respectsMaxAcceptedLimit() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 1);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.advanceAll();

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isTrue();
        assertThat(exposed.hasAccess("token-b", EVENT_ID)).isFalse();
        assertThat(queue.size()).isEqualTo(1); // token-b still waiting
    }

    @Test
    void advanceEventQueues_doesNothingWhenNoQueuesExist() {
        ExposedQueuesService exposed = createExposed();
        when(queueRepository.getAllQueues()).thenReturn(List.of());

        assertThatCode(exposed::advanceAll).doesNotThrowAnyException();
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void advanceEventQueues_evictsExpiredEntriesBeforeAdmitting() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 1);
        queue.push("token-a");
        // Admit token-a with past expiry so it occupies the one slot as expired
        queue.advanceQueue(LocalDateTime.now().minusSeconds(1));
        queue.push("token-b");
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.advanceAll(); // should evict token-a (expired), then admit token-b

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isFalse();
        assertThat(exposed.hasAccess("token-b", EVENT_ID)).isTrue();
    }

    // =========================================================================
    // getQueueAccessView — implementation-detail tests
    // =========================================================================

    @Test
    void getQueueAccessView_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueAccessView("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getQueueAccessView_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueAccessView(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    // =========================================================================
    // requestAccess — null-input guards and repository interaction
    // =========================================================================

    @Test
    void requestAccess_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.requestAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void requestAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.requestAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void requestAccess_userAlreadyAdmitted_doesNotCallUpdateQueue() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(admittedQueue(EVENT_ID, "token-a"));
        clearInvocations(queueRepository);

        QueueAccessDTO view = service.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void requestAccess_newUser_callsUpdateQueueToSaveWaitingListChange() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, 0);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.requestAccess("token-a", EVENT_ID);

        verify(queueRepository, atLeastOnce()).updateQueue(queue);
    }

    // =========================================================================
    // hasAccess — null-input guards
    // =========================================================================

    @Test
    void hasAccess_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void hasAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    // =========================================================================
    // Concurrency tests
    // =========================================================================

    @Test
    void concurrentPushes_allUniqueUsersRecordedWithNoLostUpdates() throws InterruptedException {
        int n = 20;
        List<String> tokens = generateTokens(n);
        Set<String> capturedPushes = ConcurrentHashMap.newKeySet();

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        doAnswer(inv -> { capturedPushes.add(inv.getArgument(0)); return null; })
                .when(mockQueue).push(any());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger successes = new AtomicInteger();

        for (String token : tokens) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.pushToEventQueue(EVENT_ID, token);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(n);
        assertThat(capturedPushes).containsExactlyInAnyOrderElementsOf(tokens);
    }

    @Test
    void concurrentPops_allSucceedWithDistinctValues() throws InterruptedException {
        int items   = 20;
        int threads = 10;
        List<String> tokens = generateTokens(items);
        ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>(tokens);

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        when(mockQueue.isEmpty()).thenAnswer(inv -> deque.isEmpty());
        when(mockQueue.pop()).thenAnswer(inv -> deque.poll());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<String> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String popped = service.popFromEventQueue(EVENT_ID);
                    results.add(popped);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(threads);
        assertThat(results).hasSize(threads);
    }

    @Test
    void concurrentPops_onlyAsManySucceedAsQueueSize() throws InterruptedException {
        int items   = 5;
        int threads = 20;
        List<String> tokens = generateTokens(items);
        ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>(tokens);

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        when(mockQueue.isEmpty()).thenAnswer(inv -> deque.isEmpty());
        when(mockQueue.pop()).thenAnswer(inv -> deque.poll());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<String> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes  = new AtomicInteger();
        AtomicInteger emptyThrown = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String popped = service.popFromEventQueue(EVENT_ID);
                    results.add(popped);
                    successes.incrementAndGet();
                } catch (EmptyQueueException e) {
                    emptyThrown.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isLessThanOrEqualTo(items);
        assertThat(successes.get() + emptyThrown.get()).isEqualTo(threads);
        assertThat(results).doesNotHaveDuplicates();
    }

    @Test
    void concurrentAdvanceEventQueues_noDataCorruption() throws InterruptedException {
        ExposedQueuesService exposed = createExposed();
        int userCount = 20;
        VirtualQueue queue = new VirtualQueue(EVENT_ID, Integer.MAX_VALUE, userCount);
        for (int i = 0; i < userCount; i++) {
            queue.push("token-" + i);
        }
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));

        int threads = 5;
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

        // All users should be admitted; none duplicated
        assertThat(queue.getAccessMap()).hasSize(userCount);
        assertThat(queue.isEmpty()).isTrue();
    }

    // =========================================================================
    // clearEventQueue — repository interaction
    // =========================================================================

    @Test
    void clearEventQueue_positive_clearsEntireQueueAndCallsUpdateQueue() {
        VirtualQueue queue = admittedQueue(EVENT_ID, "token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.clearEventQueue(EVENT_ID);

        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.getAccessMap()).isEmpty();
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void clearEventQueue_negative_nullEventId_doesNotHitRepository() {
        assertThatThrownBy(() -> service.clearEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void clearEventQueue_negative_queueNotFound_doesNotCallUpdate() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.clearEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    // =========================================================================
    // updateQueueSettings — repository interaction
    // =========================================================================

    @Test
    void updateQueueSettings_positive_persistsNewSettingsViaUpdateQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 100, 10);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.updateQueueSettings(EVENT_ID, 500, 50);

        assertThat(queue.getCapacity()).isEqualTo(500);
        assertThat(queue.getMaxAccepted()).isEqualTo(50);
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void updateQueueSettings_negative_nullEventId_doesNotHitRepository() {
        assertThatThrownBy(() -> service.updateQueueSettings(null, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void updateQueueSettings_negative_negativeCapacity_doesNotHitRepository() {
        assertThatThrownBy(() -> service.updateQueueSettings(EVENT_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void updateQueueSettings_negative_negativeMaxAccepted_doesNotHitRepository() {
        assertThatThrownBy(() -> service.updateQueueSettings(EVENT_ID, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void updateQueueSettings_negative_queueNotFound_doesNotCallUpdate() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.updateQueueSettings(EVENT_ID, 100, 10))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    // =========================================================================
    // getQueueSnapshot — repository interaction
    // =========================================================================

    @Test
    void getQueueSnapshot_positive_callsGetQueueAndReturnsCorrectFields() {
        VirtualQueue queue = admittedQueue(EVENT_ID, "token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        QueueSnapshotDTO snap = service.getQueueSnapshot(EVENT_ID);

        verify(queueRepository).getQueue(EVENT_ID);
        assertThat(snap.eventId()).isEqualTo(EVENT_ID);
        assertThat(snap.admittedCount()).isEqualTo(1);
        assertThat(snap.waitingCount()).isEqualTo(1);
        assertThat(snap.admittedUsers()).containsKey("token-a");
    }

    @Test
    void getQueueSnapshot_negative_nullEventId_doesNotHitRepository() {
        assertThatThrownBy(() -> service.getQueueSnapshot(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getQueueSnapshot_negative_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getQueueSnapshot(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getAllQueueSnapshots — repository interaction
    // =========================================================================

    @Test
    void getAllQueueSnapshots_positive_callsGetAllQueues() {
        UUID id2 = UUID.randomUUID();
        when(queueRepository.getAllQueues()).thenReturn(
                List.of(new VirtualQueue(EVENT_ID, 100, 10), new VirtualQueue(id2, 200, 20)));

        List<QueueSnapshotDTO> result = service.getAllQueueSnapshots();

        verify(queueRepository).getAllQueues();
        assertThat(result).hasSize(2);
    }

    @Test
    void getAllQueueSnapshots_positive_snapshotContainsCapacityAndMaxAccepted() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 500, 50);
        when(queueRepository.getAllQueues()).thenReturn(List.of(queue));

        QueueSnapshotDTO snap = service.getAllQueueSnapshots().get(0);

        assertThat(snap.capacity()).isEqualTo(500);
        assertThat(snap.maxAccepted()).isEqualTo(50);
    }

    @Test
    void getAllQueueSnapshots_positive_emptyRepositoryReturnsEmptyList() {
        when(queueRepository.getAllQueues()).thenReturn(List.of());

        assertThat(service.getAllQueueSnapshots()).isEmpty();
        verify(queueRepository).getAllQueues();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<String> generateTokens(int n) {
        List<String> tokens = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tokens.add("token-" + UUID.randomUUID());
        }
        return tokens;
    }
}
