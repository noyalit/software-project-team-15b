package com.software_project_team_15b.Ticketmaster.white.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.LotteryDomainServiceImpl;

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
 * White-box tests for {@link LotteryDomainServiceImpl}.
 *
 * <p>These tests reach past the {@link com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService}
 * contract to verify internal state and protected helpers — the winners map is inspected
 * via reflection, and an {@link ExposedLotteryService} subclass widens access to the
 * protected {@code clearWinnerAccess} method so that admin-intervention paths can be
 * exercised deterministically.
 */
@ExtendWith(MockitoExtension.class)
class LotteryDomainServiceImplWhiteTest {

    @Mock private ILotteryRepository lotteryRepository;
    @Mock private IAuth auth;
    @InjectMocks private LotteryDomainServiceImpl service;

    @BeforeEach
    void injectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    /** Promotes clearWinnerAccess from protected to public for direct test invocation. */
    private static class ExposedLotteryService extends LotteryDomainServiceImpl {
        ExposedLotteryService(ILotteryRepository r, IAuth a) {
            super(r, a);
        }

        @Override
        public synchronized void clearWinnerAccess(UUID userId, UUID eventId) {
            super.clearWinnerAccess(userId, eventId);
        }
    }

    private ExposedLotteryService createExposed() {
        ExposedLotteryService exposed = new ExposedLotteryService(lotteryRepository, auth);
        ReflectionTestUtils.setField(exposed, "self", exposed);
        return exposed;
    }

    // =========================================================================
    // Lottery CRUD — positive (verify-based)
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
    void popRandomFromEventLottery_withCount_returnsRequestedNumberOfUniqueEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.add(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.popRandomFromEventLottery(EVENT_ID, 2);

        assertThat(result).hasSize(2);
        verify(lotteryRepository).updateLottery(lottery);
    }

    // =========================================================================
    // Lottery CRUD — negative (verify-based)
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
    void popRandomFromEventLottery_withCount_emptyLottery_throwsEmptyLotteryException() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, 3))
                .isInstanceOf(EmptyLotteryException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    // =========================================================================
    // runEventLottery — verify-based
    // =========================================================================

    @Test
    void runEventLottery_updatesRepository() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1);

        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void runEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.runEventLottery(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void runEventLottery_negativeCount_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void runEventLottery_calledTwice_throwsLotteryAlreadyDrawnException() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1);

        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1))
                .isInstanceOf(LotteryAlreadyDrawnException.class);
        verify(lotteryRepository, times(1)).updateLottery(any());
    }

    @Test
    void runEventLottery_marksEventAsDrawnInWinnersMap() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1);

        assertThat(winnersMap(service)).containsKey(EVENT_ID);
    }

    // =========================================================================
    // clearWinnerAccess — internal-state tests
    // =========================================================================

    @Test
    void clearWinnerAccess_afterLotteryDeleted_doesNotThrow() {
        ExposedLotteryService exposed = createExposed();
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        exposed.runEventLottery(EVENT_ID, 1);
        winnersMap(exposed).remove(EVENT_ID);

        assertThatCode(() -> exposed.clearWinnerAccess(USER_A, EVENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void clearWinnerAccess_removesWinnerFromAdmittedSet() {
        ExposedLotteryService exposed = createExposed();
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        exposed.runEventLottery(EVENT_ID, 1);
        exposed.clearWinnerAccess(USER_A, EVENT_ID);

        LotteryEligibilityDTO result = exposed.getLotteryEligibilityForEvent(USER_A, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
    }

    @Test
    void clearWinnerAccess_doesNotTriggerAnotherDraw() {
        ExposedLotteryService exposed = createExposed();
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> drawn = exposed.runEventLottery(EVENT_ID, 1);
        UUID winner = drawn.iterator().next();
        UUID loser  = winner.equals(USER_A) ? USER_B : USER_A;

        exposed.clearWinnerAccess(winner, EVENT_ID);

        LotteryEligibilityDTO result = exposed.getLotteryEligibilityForEvent(loser, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
    }

    @Test
    void clearWinnerAccess_nullUserId_throwsIllegalArgument() {
        ExposedLotteryService exposed = createExposed();
        assertThatThrownBy(() -> exposed.clearWinnerAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearWinnerAccess_nullEventId_throwsIllegalArgument() {
        ExposedLotteryService exposed = createExposed();
        assertThatThrownBy(() -> exposed.clearWinnerAccess(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent — verify and internal-state tests
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_winnerAccessExpiredInMap_returnsAccessExpiredStatus() {
        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = new ConcurrentHashMap<>();
        eventWinners.put(USER_A, LocalDateTime.now().minusSeconds(1));
        winnersMap(service).put(EVENT_ID, eventWinners);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.ACCESS_EXPIRED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void getLotteryEligibilityForEvent_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    // =========================================================================
    // hasAccess — verify and internal-state tests
    // =========================================================================

    @Test
    void hasAccess_returnsFalseForExpiredWinner() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = new ConcurrentHashMap<>();
        eventWinners.put(USER_A, LocalDateTime.now().minusSeconds(1));
        winnersMap(service).put(EVENT_ID, eventWinners);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

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

    // =========================================================================
    // getEventLotteryWinners — verify-based negative
    // =========================================================================

    @Test
    void getEventLotteryWinners_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getEventLotteryWinners(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    // =========================================================================
    // clearEventLotteryWinners — verify-based
    // =========================================================================

    @Test
    void clearEventLotteryWinners_clearsWinnersAndUpdatesRepository() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.popRandom();
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.clearEventLotteryWinners(EVENT_ID);

        assertThat(lottery.getWinners()).isEmpty();
        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void clearEventLotteryWinners_idempotentOnAlreadyEmptyWinners() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.clearEventLotteryWinners(EVENT_ID);

        assertThat(lottery.getWinners()).isEmpty();
        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void clearEventLotteryWinners_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.clearEventLotteryWinners(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void clearEventLotteryWinners_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.clearEventLotteryWinners(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    // =========================================================================
    // drawWinnersTransactional — internal-only path
    // =========================================================================

    @Test
    void drawWinnersTransactional_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.drawWinnersTransactional(EVENT_ID, 1))
                .isInstanceOf(LotteryNotFoundException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    @Test
    void drawWinnersTransactional_returnsEmptySetOnEmptyLottery_andStillUpdatesRepository() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.drawWinnersTransactional(EVENT_ID, 5);

        assertThat(result).isEmpty();
        verify(lotteryRepository).updateLottery(lottery);
    }

    // =========================================================================
    // Concurrency tests
    // =========================================================================

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

    @Test
    void concurrentRunEventLottery_exactlyOneSucceeds() throws InterruptedException {
        Lottery lottery = new Lottery(EVENT_ID);
        for (int i = 0; i < 10; i++) lottery.add(UUID.randomUUID());
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger alreadyDrawn = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.runEventLottery(EVENT_ID, 3);
                    successes.incrementAndGet();
                } catch (LotteryAlreadyDrawnException e) {
                    alreadyDrawn.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(alreadyDrawn.get()).isEqualTo(threads - 1);
    }

    @Test
    void concurrentClearWinnerAccess_isSafeUnderContention() throws InterruptedException {
        ExposedLotteryService exposed = createExposed();
        Lottery lottery = new Lottery(EVENT_ID);
        for (int i = 0; i < 10; i++) lottery.add(UUID.randomUUID());
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> drawn = exposed.runEventLottery(EVENT_ID, 5);
        List<UUID> winnersList = new ArrayList<>(drawn);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(winnersList.size());
        AtomicInteger cleared = new AtomicInteger();

        for (UUID w : winnersList) {
            pool.submit(() -> {
                try {
                    start.await();
                    exposed.clearWinnerAccess(w, EVENT_ID);
                    cleared.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(cleared.get()).isEqualTo(winnersList.size());

        // All previously admitted winners now read as NOT_SELECTED.
        for (UUID w : winnersList) {
            assertThat(exposed.getLotteryEligibilityForEvent(w, EVENT_ID).status())
                    .isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>> winnersMap(LotteryDomainServiceImpl svc) {
        return (ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>>)
                ReflectionTestUtils.getField(svc, "winners");
    }

    private static List<UUID> generateUserIds(int n) {
        List<UUID> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }
}
