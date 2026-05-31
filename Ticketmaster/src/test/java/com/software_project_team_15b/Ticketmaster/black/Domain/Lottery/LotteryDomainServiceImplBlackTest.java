package com.software_project_team_15b.Ticketmaster.black.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.LotteryDomainServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for {@link LotteryDomainServiceImpl} exercised purely through the
 * {@link ILotteryDomainService} contract.
 *
 * <p>No reflection on the SUT, no protected-method exposure: the system is driven via
 * public interface methods and observed through public interface methods.
 */
@ExtendWith(MockitoExtension.class)
class LotteryDomainServiceImplBlackTest {

    @Mock private ILotteryRepository lotteryRepository;
    @InjectMocks private LotteryDomainServiceImpl service;

    private ILotteryDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = service;
    }

    private static final UUID EVENT_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID USER_A         = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B         = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_C         = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final LocalDateTime EXPIRY = LocalDateTime.now().plusHours(1);

    // =========================================================================
    // createEventLottery
    // =========================================================================

    @Test
    void createEventLottery_positive_returnsNormally() {
        assertThatCode(() -> domainService.createEventLottery(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void createEventLottery_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.createEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // deleteEventLottery
    // =========================================================================

    @Test
    void deleteEventLottery_positive_returnsNormally() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(new Lottery(EVENT_ID));
        assertThatCode(() -> domainService.deleteEventLottery(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deleteEventLottery_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.deleteEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventLottery_negative_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.deleteEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // addToEventLottery
    // =========================================================================

    @Test
    void addToEventLottery_positive_addsUserIntoLotteryPool() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.addToEventLottery(EVENT_ID, USER_A);

        assertThat(lottery.pop(USER_A)).isEqualTo(USER_A);
    }

    @Test
    void addToEventLottery_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.addToEventLottery(null, USER_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addToEventLottery_negative_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.addToEventLottery(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addToEventLottery_negative_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.addToEventLottery(EVENT_ID, USER_A))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // runEventLottery
    // =========================================================================

    @Test
    void runEventLottery_positive_returnsSelectedWinners() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.add(USER_C);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = domainService.runEventLottery(EVENT_ID, 2, EXPIRY);

        assertThat(result).hasSize(2);
        assertThat(Set.of(USER_A, USER_B, USER_C)).containsAll(result);
    }

    @Test
    void runEventLottery_positive_winnersHaveActiveAccess() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void runEventLottery_positive_loserHasNotSelectedStatus() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> winners = domainService.runEventLottery(EVENT_ID, 1, EXPIRY);
        UUID loser = Set.of(USER_A, USER_B).stream()
                .filter(u -> !winners.contains(u))
                .findFirst().orElseThrow();

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(loser, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void runEventLottery_positive_emptyPool_returnsEmptySetButMarksDrawn() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(new Lottery(EVENT_ID));

        Set<UUID> result = domainService.runEventLottery(EVENT_ID, 5, EXPIRY);

        assertThat(result).isEmpty();
        LotteryEligibilityDTO view = domainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID);
        assertThat(view.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
    }

    @Test
    void runEventLottery_positive_countLargerThanPool_returnsAllEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = domainService.runEventLottery(EVENT_ID, 100, EXPIRY);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    void runEventLottery_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.runEventLottery(null, 1, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_negative_nullExpirationTime_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.runEventLottery(EVENT_ID, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_negative_pastExpirationTime_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.runEventLottery(EVENT_ID, 1, LocalDateTime.now().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_negative_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.runEventLottery(EVENT_ID, 1, EXPIRY))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void runEventLottery_calledTwice_secondCallThrowsIllegalState() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThatThrownBy(() -> domainService.runEventLottery(EVENT_ID, 1, EXPIRY))
                .isInstanceOf(IllegalStateException.class);
    }

    // =========================================================================
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_positive_returnsTrueForWinnerWithActiveAccess() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThat(domainService.hasAccess(USER_A, EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsFalseForNonWinner() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThat(domainService.hasAccess(USER_B, EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_positive_returnsFalseWhenNoLotteryDrawn() {
        assertThat(domainService.hasAccess(USER_A, EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_negative_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasAccess_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // getEventLotteryWinners
    // =========================================================================

    @Test
    void getEventLotteryWinners_positive_returnsDrawnWinnersFromDomainEntity() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.popRandom(2);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = domainService.getEventLotteryWinners(EVENT_ID);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    void getEventLotteryWinners_positive_emptyWhenNobodyDrawn() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        assertThat(domainService.getEventLotteryWinners(EVENT_ID)).isEmpty();
    }

    @Test
    void getEventLotteryWinners_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getEventLotteryWinners(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEventLotteryWinners_negative_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> domainService.getEventLotteryWinners(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_positive_noLottery_returnsNoLotteryRequired() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_lotteryExistsButNotDrawn_returnsNotSelected() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(new Lottery(EVENT_ID));

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.LOTTERY_OPEN_NOT_ENTERED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_winnerReturnsWonAndAccessValid() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_negative_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getLotteryEligibilityForEvent(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLotteryEligibilityForEvent_negative_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.getLotteryEligibilityForEvent(USER_A, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLotteryEligibilityForEvent_positive_unrelatedEventReportsNoLotteryRequired() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);
        when(lotteryRepository.getLottery(OTHER_EVENT_ID)).thenReturn(null);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        LotteryEligibilityDTO result = domainService.getLotteryEligibilityForEvent(USER_A, OTHER_EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
    }

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Test
    void concurrentRunEventLottery_independentEvents_eachWinsOnce() throws InterruptedException {
        int n = 15;
        UUID[] eventIds = new UUID[n];
        for (int i = 0; i < n; i++) {
            eventIds[i] = UUID.randomUUID();
            Lottery lottery = new Lottery(eventIds[i]);
            lottery.add(UUID.randomUUID());
            when(lotteryRepository.getLottery(eventIds[i])).thenReturn(lottery);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger drawn = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final UUID eid = eventIds[i];
            pool.submit(() -> {
                try {
                    start.await();
                    Set<UUID> result = domainService.runEventLottery(eid, 1, EXPIRY);
                    if (result.size() == 1) drawn.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(drawn.get()).isEqualTo(n);
    }

    @Test
    void runEventLottery_positive_countBoundedByPoolSize() {
        Lottery lottery = new Lottery(EVENT_ID);
        for (int i = 0; i < 4; i++) lottery.add(UUID.randomUUID());
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = domainService.runEventLottery(EVENT_ID, 3, EXPIRY);

        assertThat(result).hasSize(3);
        // Lottery is now marked drawn; any subsequent call must be rejected
        assertThatThrownBy(() -> domainService.runEventLottery(EVENT_ID, 3, EXPIRY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentHasAccess_winnerAdmitted_allReadersSeeTrue() throws InterruptedException {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        domainService.runEventLottery(EVENT_ID, 1, EXPIRY);

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (domainService.hasAccess(USER_A, EVENT_ID)) trueCount.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(trueCount.get()).isEqualTo(threads);
    }
}
