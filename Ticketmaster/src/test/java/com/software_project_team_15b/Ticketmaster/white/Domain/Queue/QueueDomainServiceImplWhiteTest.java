package com.software_project_team_15b.Ticketmaster.white.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
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
 * <p>These tests reach past the {@link com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService}
 * contract to verify internal state and protected helpers. The {@link #eventAccessMap} helper
 * inspects the internal {@code eventAccess} map via reflection. An {@link ExposedQueuesService}
 * subclass widens access to the protected {@code clearEventAccess} method so that the
 * scheduler-driven code paths can be exercised deterministically.
 *
 * <p>Site-queue operations ({@code addUserToSiteQueue}, {@code validateAndExitQueue},
 * {@code canAccessWebsite}) are managed by the application-layer {@code QueueService} and are
 * not implemented by this domain service; tests for those paths verify that
 * {@link UnsupportedOperationException} is thrown.
 */
@ExtendWith(MockitoExtension.class)
class QueueDomainServiceImplWhiteTest {

    @Mock private IQueueRepository queueRepository;
    @InjectMocks private QueueDomainServiceImpl service;

    @BeforeEach
    void injectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    private ExposedQueuesService createExposed() {
        ExposedQueuesService exposed = new ExposedQueuesService(queueRepository);
        ReflectionTestUtils.setField(exposed, "self", exposed);
        return exposed;
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Subclass that widens the protected {@code clearEventAccess} method so tests can
     * deterministically trigger the scheduler-driven access-expiry path.
     */
    private static class ExposedQueuesService extends QueueDomainServiceImpl {
        ExposedQueuesService(IQueueRepository r) {
            super(r);
        }

        @Override
        public synchronized void clearEventAccess(String token, UUID eventId) {
            super.clearEventAccess(token, eventId);
        }
    }

    // =========================================================================
    // Site-queue stubs — these operations are unsupported in this implementation
    // =========================================================================

    @Test
    void addUserToSiteQueue_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validateAndExitQueue_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> service.validateAndExitQueue("token-a"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void canAccessWebsite_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> service.canAccessWebsite())
                .isInstanceOf(UnsupportedOperationException.class);
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
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(new VirtualQueue(EVENT_ID));

        service.createEventQueue(EVENT_ID);

        verify(queueRepository).addQueue(any(VirtualQueue.class));
    }

    @Test
    void deleteEventQueue_callsRemoveQueueOnRepository() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.deleteEventQueue(EVENT_ID);

        verify(queueRepository).removeQueue(queue);
    }

    @Test
    void pushToEventQueue_addsUserToQueueAndUpdatesRepository() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.pushToEventQueue(EVENT_ID, "token-a");

        assertThat(queue.contains("token-a")).isTrue();
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void popFromEventQueue_returnsFrontUserAndUpdatesRepository() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
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
        assertThatThrownBy(() -> service.createEventQueue(null))
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
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 1);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-b"))
                .isInstanceOf(QueueIsFullException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void pushToEventQueue_alreadyInQueue_throwsAlreadyInQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
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
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(EmptyQueueException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    // =========================================================================
    // clearEventAccess — ExposedQueuesService (whitebox protected method)
    // =========================================================================

    @Test
    void clearEventAccess_removesUserFromEventAccess() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        // createEventQueue advances the queue, admitting "token-a"
        exposed.createEventQueue(EVENT_ID);
        exposed.clearEventAccess("token-a", EVENT_ID);

        assertThat(exposed.isUserAdmitted("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void clearEventAccess_advancesNextUserFromQueueIntoEventAccess() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        // After createEventQueue: token-a and token-b are both admitted (queue drains)
        exposed.createEventQueue(EVENT_ID);

        // Add token-c to the now-empty queue
        queue.push("token-c");
        // Clearing token-a triggers advanceEventQueue, which promotes token-c
        exposed.clearEventAccess("token-a", EVENT_ID);

        assertThat(exposed.isUserAdmitted("token-a", EVENT_ID)).isFalse();
        assertThat(exposed.isUserAdmitted("token-b", EVENT_ID)).isTrue();
        assertThat(exposed.isUserAdmitted("token-c", EVENT_ID)).isTrue();
    }

    @Test
    void clearEventAccess_afterQueueDeleted_doesNotThrow() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.createEventQueue(EVENT_ID);
        exposed.deleteEventQueue(EVENT_ID);

        assertThatCode(() -> exposed.clearEventAccess("token-a", EVENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void clearEventAccess_nullToken_throwsIllegalArgument() {
        ExposedQueuesService exposed = createExposed();
        assertThatThrownBy(() -> exposed.clearEventAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearEventAccess_nullEventId_throwsIllegalArgument() {
        ExposedQueuesService exposed = createExposed();
        assertThatThrownBy(() -> exposed.clearEventAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // isUserAdmitted — internal-map inspection
    // =========================================================================

    @Test
    void isUserAdmitted_returnsTrue_whenTokenPresentInEventAccessMap() {
        ConcurrentHashMap<String, LocalDateTime> access = new ConcurrentHashMap<>();
        access.put("token-a", LocalDateTime.now().plusSeconds(100));
        eventAccessMap(service).put(EVENT_ID, access);

        assertThat(service.isUserAdmitted("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void isUserAdmitted_returnsFalse_whenTokenNotInEventAccessMap() {
        ConcurrentHashMap<String, LocalDateTime> access = new ConcurrentHashMap<>();
        eventAccessMap(service).put(EVENT_ID, access);

        assertThat(service.isUserAdmitted("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void isUserAdmitted_returnsFalse_whenEventNotInAccessMap() {
        assertThat(service.isUserAdmitted("token-a", EVENT_ID)).isFalse();
    }

    // =========================================================================
    // getQueueAccessView — implementation-detail tests
    // =========================================================================

    @Test
    void getQueueAccessView_accessExpiresAt_matchesScheduledWindow() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        LocalDateTime before = LocalDateTime.now();
        service.createEventQueue(EVENT_ID);
        LocalDateTime after = LocalDateTime.now();

        QueueAccessDTO view = service.getQueueAccessView("token-a", EVENT_ID);

        // bounds come from the private ACCESS_TIME = 100 constant
        assertThat(view.accessExpiresAt()).isAfterOrEqualTo(before.plusSeconds(100));
        assertThat(view.accessExpiresAt()).isBeforeOrEqualTo(after.plusSeconds(100));
    }

    @Test
    void getQueueAccessView_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueAccessView("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    // =========================================================================
    // requestAccess — null-input guards
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

        int implicitEmpties = threads - successes.get() - emptyThrown.get();
        assertThat(successes.get()).isLessThanOrEqualTo(items);
        assertThat(successes.get() + emptyThrown.get() + implicitEmpties).isEqualTo(threads);
        assertThat(results).doesNotHaveDuplicates();
        assertThat(deque).isEmpty();
    }

    // =========================================================================
    // requestAccess — internal-state side effects
    // =========================================================================

    @Test
    void requestAccess_whenSlotsAvailable_promotesUserIntoEventAccessMap() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.createEventQueue(EVENT_ID);
        QueueAccessDTO view = service.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(eventAccessMap(service).get(EVENT_ID)).containsKey("token-a");
    }

    @Test
    void requestAccess_userAlreadyAdmitted_doesNotPushAgain() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.createEventQueue(EVENT_ID);
        // token-a is now admitted; clear setup interactions to isolate the re-call
        clearInvocations(queueRepository);

        QueueAccessDTO view = service.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        verify(queueRepository, never()).updateQueue(any());
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

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<UUID, ConcurrentHashMap<String, LocalDateTime>> eventAccessMap(QueueDomainServiceImpl svc) {
        return (ConcurrentHashMap<UUID, ConcurrentHashMap<String, LocalDateTime>>)
                ReflectionTestUtils.getField(svc, "eventAccess");
    }
}
