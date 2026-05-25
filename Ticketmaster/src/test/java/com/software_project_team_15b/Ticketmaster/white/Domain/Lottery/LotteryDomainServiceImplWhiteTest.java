package com.software_project_team_15b.Ticketmaster.white.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.LotteryDomainServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * contract to verify internal state — the winners map is inspected via reflection.
 */
@ExtendWith(MockitoExtension.class)
class LotteryDomainServiceImplWhiteTest {

    @Mock private ILotteryRepository lotteryRepository;
    @InjectMocks private LotteryDomainServiceImpl service;

    /**
     * Subclass that widens the protected helpers so tests can invoke them
     * deterministically.
     */
    private static class ExposedLotteryService extends LotteryDomainServiceImpl {
        ExposedLotteryService(ILotteryRepository r) { super(r); }

        @Override
        public UUID popRandomFromEventLottery(UUID eventId) {
            return super.popRandomFromEventLottery(eventId);
        }

        @Override
        public Set<UUID> popRandomFromEventLottery(UUID eventId, int count) {
            return super.popRandomFromEventLottery(eventId, count);
        }
    }

    private ExposedLotteryService createExposed() {
        return new ExposedLotteryService(lotteryRepository);
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime EXPIRY = LocalDateTime.now().plusHours(1);

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

        UUID result = createExposed().popRandomFromEventLottery(EVENT_ID);

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

        Set<UUID> result = createExposed().popRandomFromEventLottery(EVENT_ID, 2);

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
        ExposedLotteryService exposed = createExposed();
        assertThatThrownBy(() -> exposed.popRandomFromEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_emptyLottery_throwsEmptyLotteryException() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThatThrownBy(() -> createExposed().popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(EmptyLotteryException.class);
        verify(lotteryRepository, never()).updateLottery(any());
    }

    @Test
    void popRandomFromEventLottery_withCount_nullEventId_throwsIllegalArgument() {
        ExposedLotteryService exposed = createExposed();
        assertThatThrownBy(() -> exposed.popRandomFromEventLottery(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_withCount_negativeCount_throwsIllegalArgument() {
        ExposedLotteryService exposed = createExposed();
        assertThatThrownBy(() -> exposed.popRandomFromEventLottery(EVENT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void popRandomFromEventLottery_withCount_emptyLottery_throwsEmptyLotteryException() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThatThrownBy(() -> createExposed().popRandomFromEventLottery(EVENT_ID, 3))
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

        service.runEventLottery(EVENT_ID, 1, EXPIRY);

        verify(lotteryRepository).updateLottery(lottery);
    }

    @Test
    void runEventLottery_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.runEventLottery(null, 1, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void runEventLottery_nullExpirationTime_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void runEventLottery_pastExpirationTime_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1, LocalDateTime.now().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lotteryRepository);
    }

    @Test
    void runEventLottery_calledTwice_secondCallReturnsEmptySet() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1, EXPIRY);
        Set<UUID> second = service.runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThat(second).isEmpty();
        verify(lotteryRepository, times(2)).updateLottery(any());
    }

    @Test
    void runEventLottery_setsExpirationTimeOnLottery() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThat(lottery.getExpirationTime()).isEqualTo(EXPIRY);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent — verify and internal-state tests
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_winnerAccessExpired_returnsAccessExpiredStatus() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.popRandom(1);  // USER_A is now a winner (only entry)
        lottery.setExpirationTime(LocalDateTime.now().minusSeconds(1));
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

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
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.popRandom(1);  // USER_A is now a winner
        lottery.setExpirationTime(LocalDateTime.now().minusSeconds(1));
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThat(service.hasAccess(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.hasAccess(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
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

        ExposedLotteryService exposed = createExposed();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<UUID> results = ConcurrentHashMap.newKeySet();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UUID popped = exposed.popRandomFromEventLottery(EVENT_ID);
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
    void concurrentRunEventLottery_allCallsCompleteWithoutException() throws InterruptedException {
        Lottery lottery = new Lottery(EVENT_ID);
        for (int i = 0; i < 10; i++) lottery.add(UUID.randomUUID());
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.runEventLottery(EVENT_ID, 1, EXPIRY);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(threads);
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
