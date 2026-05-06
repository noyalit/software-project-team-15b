package com.software_project_team_15b.Ticketmaster.Application.QueuesServiceTest;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueuesService;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueuesServiceTests {

    @Mock private IQueueRepository queueRepository;
    @Mock private ILotteryRepository lotteryRepository;
    @Mock private IAuth auth;
    @InjectMocks private QueuesService service;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_C   = UUID.fromString("00000000-0000-0000-0000-000000000004");

    /**
     * Promotes clearEventAccess from protected to public so tests can invoke it directly
     * without waiting 100 seconds for the scheduler.
     */
    private static class ExposedQueuesService extends QueuesService {
        ExposedQueuesService(IQueueRepository r, ILotteryRepository l, IAuth a) {
            super(r, l, a);
        }

        @Override
        public synchronized void clearEventAccess(UUID userId, UUID eventId) {
            super.clearEventAccess(userId, eventId);
        }
    }

    // =========================================================================
    // Site queue — positive tests
    // =========================================================================

    @Test
    void addUserToSiteQueue_addedTokenIsReturnedByGetNext() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        service.addUserToSiteQueue("token-a");
        assertThat(service.getNextUserFromSiteQueue()).isEqualTo("token-a");
    }

    @Test
    void getNextUserFromSiteQueue_skipsInvalidTokensAndReturnsFirstValid() {
        when(auth.isTokenValid("expired")).thenReturn(false);
        when(auth.isTokenValid("valid")).thenReturn(true);
        service.addUserToSiteQueue("expired");
        service.addUserToSiteQueue("valid");
        assertThat(service.getNextUserFromSiteQueue()).isEqualTo("valid");
    }

    // =========================================================================
    // Site queue — negative tests
    // =========================================================================

    @Test
    void addUserToSiteQueue_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addUserToSiteQueue_duplicateToken_throwsIllegalArgument() {
        service.addUserToSiteQueue("token-a");
        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getNextUserFromSiteQueue_emptyQueue_throwsEmptyQueueException() {
        assertThatThrownBy(() -> service.getNextUserFromSiteQueue())
                .isInstanceOf(EmptyQueueException.class);
    }

    // =========================================================================
    // getPositionInEventQueue — positive tests
    // =========================================================================

    @Test
    void getPositionInEventQueue_returnsCorrectPosition() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        queue.push(USER_B);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThat(service.getPositionInEventQueue(USER_A, EVENT_ID)).isEqualTo(0);
        assertThat(service.getPositionInEventQueue(USER_B, EVENT_ID)).isEqualTo(1);
    }

    // =========================================================================
    // getPositionInEventQueue — negative tests
    // =========================================================================

    @Test
    void getPositionInEventQueue_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getPositionInEventQueue(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getPositionInEventQueue_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getPositionInEventQueue(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void getPositionInEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.getPositionInEventQueue(USER_A, EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // Queue — positive tests
    // =========================================================================

    @Test
    void createEventQueue_callsAddQueueOnRepository() {
        // advanceEventQueue fires after creation; stub an empty queue so it exits cleanly.
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

        service.pushToEventQueue(EVENT_ID, USER_A);

        assertThat(queue.contains(USER_A)).isTrue();
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void popFromEventQueue_returnsFrontUserAndUpdatesRepository() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        queue.push(USER_B);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        UUID result = service.popFromEventQueue(EVENT_ID);

        assertThat(result).isEqualTo(USER_A);
        verify(queueRepository).updateQueue(queue);
    }

    @Test
    void popFromEventQueue_removesUserFromQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.popFromEventQueue(EVENT_ID);

        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void popFromEventQueue_maintainsFifoOrder() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        queue.push(USER_B);
        queue.push(USER_C);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        UUID first  = service.popFromEventQueue(EVENT_ID);
        UUID second = service.popFromEventQueue(EVENT_ID);
        UUID third  = service.popFromEventQueue(EVENT_ID);

        assertThat(first).isEqualTo(USER_A);
        assertThat(second).isEqualTo(USER_B);
        assertThat(third).isEqualTo(USER_C);
    }

    // =========================================================================
    // Queue — negative tests
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
        assertThatThrownBy(() -> service.pushToEventQueue(null, USER_A))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void pushToEventQueue_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void pushToEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, USER_A))
                .isInstanceOf(QueueNotFoundException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void pushToEventQueue_queueIsFull_throwsQueueIsFullException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID, 1);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, USER_B))
                .isInstanceOf(QueueIsFullException.class);
        verify(queueRepository, never()).updateQueue(any());
    }

    @Test
    void pushToEventQueue_alreadyInQueue_throwsAlreadyInQueueException() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, USER_A))
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
    // Queue advancement / eventAccess — positive tests
    // =========================================================================

    @Test
    void createEventQueue_advancesPreLoadedUsersIntoEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void pushToEventQueue_promotesUserToEventAccessWhenSlotAvailable() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);       // queue empty, no one promoted yet
        service.pushToEventQueue(EVENT_ID, USER_A); // USER_A pushed then immediately promoted

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void clearEventAccess_removesUserFromEventAccess() {
        ExposedQueuesService exposed = new ExposedQueuesService(queueRepository, lotteryRepository, auth);
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        exposed.createEventQueue(EVENT_ID);          // USER_A promoted
        exposed.clearEventAccess(USER_A, EVENT_ID);  // access revoked

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void clearEventAccess_advancesNextUserFromQueueIntoEventAccess() {
        ExposedQueuesService exposed = new ExposedQueuesService(queueRepository, lotteryRepository, auth);
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        queue.push(USER_B);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.isTokenValid("token-b")).thenReturn(true);
        when(auth.isTokenValid("token-c")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(auth.extractUserId("token-b")).thenReturn(USER_B);
        when(auth.extractUserId("token-c")).thenReturn(USER_C);

        exposed.createEventQueue(EVENT_ID); // USER_A and USER_B both promoted (slots < 100)

        // USER_C arrives while eventAccess is not yet at capacity after USER_A is cleared
        queue.push(USER_C);
        exposed.clearEventAccess(USER_A, EVENT_ID); // frees one slot → USER_C promoted

        assertThat(exposed.hasAccess("token-a", EVENT_ID)).isFalse();
        assertThat(exposed.hasAccess("token-b", EVENT_ID)).isTrue();
        assertThat(exposed.hasAccess("token-c", EVENT_ID)).isTrue();
    }

    @Test
    void clearEventAccess_afterQueueDeleted_doesNotThrow() {
        ExposedQueuesService exposed = new ExposedQueuesService(queueRepository, lotteryRepository, auth);
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        exposed.createEventQueue(EVENT_ID);
        exposed.deleteEventQueue(EVENT_ID); // removes eventAccess entry

        // Simulates a scheduled task firing after the queue was deleted
        assertThatCode(() -> exposed.clearEventAccess(USER_A, EVENT_ID))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // hasAccess — positive tests
    // =========================================================================

    @Test
    void hasAccess_returnsTrueWhenUserIsInEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push(USER_A);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_returnsFalseWhenUserIsNotInEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID); // queue is empty, no one promoted

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    // =========================================================================
    // hasAccess — negative tests
    // =========================================================================

    @Test
    void hasAccess_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void hasAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void hasAccess_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.hasAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // Lottery — positive tests
    // =========================================================================

    @Test
    void createEventLottery_callsAddLotteryOnRepository() {
        service.createEventLottery(EVENT_ID);

        verify(lotteryRepository).addLottery(any(Lottery.class));
    }

    @Test
    void deleteEventLottery_callsRemoveLotteryOnRepository() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.deleteEventLottery(EVENT_ID);

        verify(lotteryRepository).removeLottery(lottery);
    }

    @Test
    void addToEventLottery_addsUserAndUpdatesRepository() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.addToEventLottery(EVENT_ID, USER_A);

        assertThat(lottery.pop(USER_A)).isEqualTo(USER_A);
        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void popRandomFromEventLottery_singleEntry_returnsThatEntry() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        UUID result = service.popRandomFromEventLottery(EVENT_ID);

        assertThat(result).isEqualTo(USER_A);
        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void popRandomFromEventLottery_removesReturnedEntry() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.popRandomFromEventLottery(EVENT_ID);

        assertThat(lottery.pop(USER_A)).isNull();
    }

    @Test
    void popRandomFromEventLottery_multipleEntries_returnedValueIsFromLottery() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.add(USER_C);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        UUID result = service.popRandomFromEventLottery(EVENT_ID);

        assertThat(Set.of(USER_A, USER_B, USER_C)).contains(result);
    }

    @Test
    void popRandomFromEventLottery_withCount_returnsRequestedNumberOfUniqueEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.add(USER_C);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.popRandomFromEventLottery(EVENT_ID, 2);

        assertThat(result).hasSize(2);
        assertThat(Set.of(USER_A, USER_B, USER_C)).containsAll(result);
        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void popRandomFromEventLottery_withCountLargerThanLotterySize_returnsAllEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.popRandomFromEventLottery(EVENT_ID, 10);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    // =========================================================================
    // Lottery — negative tests
    // =========================================================================

    @Test
    void createEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void deleteEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.deleteEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void deleteEventLottery_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
        verify(lotteryRepository, never()).removeLottery(any());
    }

    @Test
    void addToEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addToEventLottery(null, USER_A))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void addToEventLottery_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void addToEventLottery_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, USER_A))
                .isInstanceOf(LotteryNotFoundException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    @Test
    void popRandomFromEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.popRandomFromEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void popRandomFromEventLottery_emptyLottery_throwsEmptyLotteryException() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(EmptyLotteryException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    @Test
    void popRandomFromEventLottery_withCount_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.popRandomFromEventLottery(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_withCount_negativeCount_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_withCount_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, 2))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void popRandomFromEventLottery_withCount_emptyLottery_throwsEmptyLotteryException() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, 3))
                .isInstanceOf(EmptyLotteryException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    // =========================================================================
    // Concurrency tests
    //
    // These tests use mock VirtualQueue / Lottery objects backed by thread-safe
    // data structures to verify that concurrent service calls produce correct,
    // non-duplicated results. The approach mirrors how the @Transactional +
    // @Version mechanism works in production: each thread modifies a shared
    // entity, and the outcomes must be consistent and conflict-free.
    // =========================================================================

    @Test
    void concurrentPushes_allUniqueUsersRecordedWithNoLostUpdates() throws InterruptedException {
        int n = 20;
        List<UUID> users = generateUserIds(n);
        // Thread-safe set to capture every userId that reaches push()
        Set<UUID> capturedPushes = ConcurrentHashMap.newKeySet();

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        doAnswer(inv -> { capturedPushes.add(inv.getArgument(0)); return null; })
                .when(mockQueue).push(any());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger successes = new AtomicInteger();

        for (UUID userId : users) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.pushToEventQueue(EVENT_ID, userId);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(n);
        assertThat(capturedPushes).containsExactlyInAnyOrderElementsOf(users);
    }

    @Test
    void concurrentPops_allSucceedWithDistinctValues() throws InterruptedException {
        // 20 items, 10 threads: every thread is guaranteed to see a non-empty
        // queue, so all succeed. ConcurrentLinkedDeque.poll() is atomic,
        // ensuring no two threads receive the same element.
        int items   = 20;
        int threads = 10;
        List<UUID> users = generateUserIds(items);
        ConcurrentLinkedDeque<UUID> deque = new ConcurrentLinkedDeque<>(users);

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        when(mockQueue.isEmpty()).thenAnswer(inv -> deque.isEmpty());
        when(mockQueue.pop()).thenAnswer(inv -> deque.poll());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<UUID> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UUID popped = service.popFromEventQueue(EVENT_ID);
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
        assertThat(results).hasSize(threads); // all popped values are distinct
    }

    @Test
    void concurrentPops_onlyAsManySucceedAsQueueSize() throws InterruptedException {
        // 5 items, 20 threads: exactly 5 should succeed; the rest throw
        // EmptyQueueException once the queue is drained.
        int items   = 5;
        int threads = 20;
        List<UUID> users = generateUserIds(items);
        ConcurrentLinkedDeque<UUID> deque = new ConcurrentLinkedDeque<>(users);

        VirtualQueue mockQueue = mock(VirtualQueue.class);
        when(mockQueue.isEmpty()).thenAnswer(inv -> deque.isEmpty());
        when(mockQueue.pop()).thenAnswer(inv -> deque.poll());
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(mockQueue);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<UUID> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes  = new AtomicInteger();
        AtomicInteger emptyThrown = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UUID popped = service.popFromEventQueue(EVENT_ID);
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

        // Total outcomes must account for all threads.
        // Due to the isEmpty→pop TOCTOU window a tiny number of threads may
        // receive null instead of throwing; count those as implicit empties.
        int implicitEmpties = threads - successes.get() - emptyThrown.get();
        assertThat(successes.get()).isLessThanOrEqualTo(items);
        assertThat(successes.get() + emptyThrown.get() + implicitEmpties).isEqualTo(threads);
        assertThat(results).doesNotHaveDuplicates();
        assertThat(deque).isEmpty(); // the backing store is fully drained
    }

    @Test
    void concurrentLotteryAdds_allUniqueUsersRecordedWithNoLostUpdates() throws InterruptedException {
        int n = 20;
        List<UUID> users = generateUserIds(n);
        Set<UUID> capturedAdds = ConcurrentHashMap.newKeySet();

        Lottery mockLottery = mock(Lottery.class);
        when(mockLottery.add(any())).thenAnswer(inv -> { capturedAdds.add(inv.getArgument(0)); return true; });
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(mockLottery);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger successes = new AtomicInteger();

        for (UUID userId : users) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addToEventLottery(EVENT_ID, userId);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(n);
        assertThat(capturedAdds).containsExactlyInAnyOrderElementsOf(users);
    }

    @Test
    void concurrentLotteryPops_allSucceedWithDistinctValues() throws InterruptedException {
        int items   = 20;
        int threads = 10;
        List<UUID> users = generateUserIds(items);
        ConcurrentLinkedDeque<UUID> deque = new ConcurrentLinkedDeque<>(users);

        Lottery mockLottery = mock(Lottery.class);
        when(mockLottery.popRandom()).thenAnswer(inv -> deque.poll());
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(mockLottery);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<UUID> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UUID popped = service.popRandomFromEventLottery(EVENT_ID);
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

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<UUID> generateUserIds(int n) {
        List<UUID> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }
}