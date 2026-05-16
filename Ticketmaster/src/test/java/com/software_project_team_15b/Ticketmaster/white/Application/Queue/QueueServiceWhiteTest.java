package com.software_project_team_15b.Ticketmaster.white.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
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

@ExtendWith(MockitoExtension.class)
class QueueServiceWhiteTest {

    @Mock private IQueueRepository queueRepository;
    @Mock private IAuth auth;
    @InjectMocks private QueueService service;

    @BeforeEach
    void injectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    private ExposedQueuesService createExposed() {
        ExposedQueuesService exposed = new ExposedQueuesService(queueRepository, auth);
        ReflectionTestUtils.setField(exposed, "self", exposed);
        return exposed;
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_C   = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static class ExposedQueuesService extends QueueService {
        ExposedQueuesService(IQueueRepository r, IAuth a) {
            super(r, a);
        }

        @Override
        public synchronized void clearEventAccess(UUID userId, UUID eventId) {
            super.clearEventAccess(userId, eventId);
        }
    }

    // =========================================================================
    // Site queue — reflection/invokeAcceptUsers tests
    // =========================================================================

    @Test
    void addUserToSiteQueue_tokenAppearsInQueue() {
        service.addUserToSiteQueue("token-a");
        assertThat(siteQueue(service)).contains("token-a");
    }

    @Test
    void addUserToSiteQueue_maintainsFifoOrder() {
        service.addUserToSiteQueue("token-a");
        service.addUserToSiteQueue("token-b");
        service.addUserToSiteQueue("token-c");
        assertThat(siteQueue(service)).containsExactly("token-a", "token-b", "token-c");
    }

    @Test
    void acceptUsersFromSiteQueue_admitsValidTokensAndDrainsQueue() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.isTokenValid("token-b")).thenReturn(true);
        service.addUserToSiteQueue("token-a");
        service.addUserToSiteQueue("token-b");

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("token-a")).isTrue();
        assertThat(service.validateAndExitQueue("token-b")).isTrue();
        assertThat(siteQueue(service)).isEmpty();
    }

    @Test
    void acceptUsersFromSiteQueue_skipsAndDiscardsExpiredTokensInQueue() {
        when(auth.isTokenValid("expired")).thenReturn(false);
        when(auth.isTokenValid("valid")).thenReturn(true);
        service.addUserToSiteQueue("expired");
        service.addUserToSiteQueue("valid");

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("expired")).isFalse();
        assertThat(service.validateAndExitQueue("valid")).isTrue();
        assertThat(siteQueue(service)).isEmpty();
    }

    @Test
    void acceptUsersFromSiteQueue_evictsExpiredTokensFromAcceptedSet() {
        acceptedTokens(service).add("expired");
        when(auth.isTokenValid("expired")).thenReturn(false);

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("expired")).isFalse();
    }

    @Test
    void acceptUsersFromSiteQueue_freesSlotsAndAdmitsWaitingUsersAfterEviction() {
        acceptedTokens(service).add("expired");
        when(auth.isTokenValid("expired")).thenReturn(false);
        when(auth.isTokenValid("waiting")).thenReturn(true);
        service.addUserToSiteQueue("waiting");

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("expired")).isFalse();
        assertThat(service.validateAndExitQueue("waiting")).isTrue();
    }

    @Test
    void acceptUsersFromSiteQueue_stopsAdmittingWhenMaxVisitorsReached() {
        when(auth.isTokenValid(anyString())).thenReturn(true);
        Set<String> accepted = acceptedTokens(service);
        for (int i = 0; i < 100; i++) {
            accepted.add("admitted-" + i);
        }
        service.addUserToSiteQueue("overflow");

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("overflow")).isFalse();
        assertThat(siteQueue(service)).contains("overflow");
    }

    @Test
    void validateAndExitQueue_returnsTrue_afterSchedulerAdmitsToken() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        service.addUserToSiteQueue("token-a");
        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("token-a")).isTrue();
    }

    @Test
    void validateAndExitQueue_returnsFalse_afterTokenExpiresAndSchedulerEvictsIt() {
        acceptedTokens(service).add("token-a");
        when(auth.isTokenValid("token-a")).thenReturn(false);

        invokeAcceptUsers(service);

        assertThat(service.validateAndExitQueue("token-a")).isFalse();
    }

    @Test
    void canAccessWebsite_returnsTrue_whenOneSlotRemaining() {
        Set<String> accepted = acceptedTokens(service);
        for (int i = 0; i < 99; i++) {
            accepted.add("token-" + i);
        }
        assertThat(service.canAccessWebsite()).isTrue();
    }

    @Test
    void canAccessWebsite_returnsFalse_whenAtCapacity() {
        Set<String> accepted = acceptedTokens(service);
        for (int i = 0; i < 100; i++) {
            accepted.add("token-" + i);
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
    // clearEventAccess — ExposedQueuesService (whitebox internal method)
    // =========================================================================

    @Test
    void clearEventAccess_removesUserFromEventAccess() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        exposed.createEventQueue(EVENT_ID);
        exposed.clearEventAccess(USER_A, EVENT_ID);

        assertThat(exposed.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void clearEventAccess_advancesNextUserFromQueueIntoEventAccess() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(auth.extractUserId("token-b")).thenReturn(USER_B);
        when(auth.extractUserId("token-c")).thenReturn(USER_C);

        exposed.createEventQueue(EVENT_ID);

        queue.push("token-c");
        exposed.clearEventAccess(USER_A, EVENT_ID);

        assertThat(exposed.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
        assertThat(exposed.isUserAdmitted(USER_B, EVENT_ID)).isTrue();
        assertThat(exposed.isUserAdmitted(USER_C, EVENT_ID)).isTrue();
    }

    @Test
    void clearEventAccess_afterQueueDeleted_doesNotThrow() {
        ExposedQueuesService exposed = createExposed();
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.createEventQueue(EVENT_ID);
        exposed.deleteEventQueue(EVENT_ID);

        assertThatCode(() -> exposed.clearEventAccess(USER_A, EVENT_ID))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // isUserAdmitted — whitebox tests
    // =========================================================================

    @Test
    void isUserAdmitted_returnsTrue_whenUserPresentInEventAccessMap() {
        ConcurrentHashMap<UUID, LocalDateTime> access = new ConcurrentHashMap<>();
        access.put(USER_A, LocalDateTime.now().plusSeconds(100));
        eventAccessMap(service).put(EVENT_ID, access);

        assertThat(service.isUserAdmitted(USER_A, EVENT_ID)).isTrue();
    }

    @Test
    void isUserAdmitted_returnsFalse_whenUserNotInEventAccessMap() {
        ConcurrentHashMap<UUID, LocalDateTime> access = new ConcurrentHashMap<>();
        eventAccessMap(service).put(EVENT_ID, access);

        assertThat(service.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void isUserAdmitted_returnsFalse_whenEventNotInAccessMap() {
        assertThat(service.isUserAdmitted(USER_A, EVENT_ID)).isFalse();
    }

    // =========================================================================
    // getQueueAccessView — implementation-detail tests
    // =========================================================================

    @Test
    void getQueueAccessView_accessExpiresAt_matchesScheduledWindow() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        LocalDateTime before = LocalDateTime.now();
        service.createEventQueue(EVENT_ID);
        LocalDateTime after = LocalDateTime.now();

        com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO view =
                service.getQueueAccessView("token-a", EVENT_ID);

        // bounds come from the private ACCESS_TIME = 100 constant
        assertThat(view.accessExpiresAt()).isAfterOrEqualTo(before.plusSeconds(100));
        assertThat(view.accessExpiresAt()).isBeforeOrEqualTo(after.plusSeconds(100));
    }

    @Test
    void getQueueAccessView_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueAccessView(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void getQueueAccessView_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getQueueAccessView("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth);
    }

    // =========================================================================
    // Concurrency tests
    // =========================================================================

    @Test
    void concurrentAddToSiteQueue_sameToken_exactlyOneSucceeds() throws InterruptedException {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addUserToSiteQueue("shared-token");
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
        assertThat(siteQueue(service)).containsExactly("shared-token");
    }

    @Test
    void concurrentAddToSiteQueue_distinctTokens_allSucceed() throws InterruptedException {
        int n = 30;
        List<String> tokens = generateTokens(n);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger successes = new AtomicInteger();

        for (String token : tokens) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addUserToSiteQueue(token);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(n);
        assertThat(siteQueue(service)).containsExactlyInAnyOrderElementsOf(tokens);
    }

    @Test
    void concurrentAddAndAcceptSiteQueue_noTokensLost() throws InterruptedException {
        int n = 50;
        List<String> tokens = generateTokens(n);
        when(auth.isTokenValid(anyString())).thenReturn(true);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n + 2);
        AtomicInteger addSuccesses = new AtomicInteger();

        for (String token : tokens) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addUserToSiteQueue(token);
                    addSuccesses.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    invokeAcceptUsers(service);
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        invokeAcceptUsers(service);

        int totalTracked = acceptedTokens(service).size() + siteQueue(service).size();
        assertThat(totalTracked).isEqualTo(addSuccesses.get());
    }

    @Test
    void concurrentValidateAndExitQueue_allThreadsSeeConsistentAdmittedState() throws InterruptedException {
        int threads = 30;
        when(auth.isTokenValid("token-a")).thenReturn(true);
        service.addUserToSiteQueue("token-a");
        invokeAcceptUsers(service);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (service.validateAndExitQueue("token-a")) {
                        trueCount.incrementAndGet();
                    }
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
    private static ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>> eventAccessMap(QueueService svc) {
        return (ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>>)
                ReflectionTestUtils.getField(svc, "eventAccess");
    }

    @SuppressWarnings("unchecked")
    private static Queue<String> siteQueue(QueueService svc) {
        return (Queue<String>) ReflectionTestUtils.getField(svc, "siteQueue");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> acceptedTokens(QueueService svc) {
        return (Set<String>) ReflectionTestUtils.getField(svc, "acceptedTokens");
    }

    private static void invokeAcceptUsers(QueueService svc) {
        ReflectionTestUtils.invokeMethod(svc, "acceptUsersFromSiteQueue");
    }
}
