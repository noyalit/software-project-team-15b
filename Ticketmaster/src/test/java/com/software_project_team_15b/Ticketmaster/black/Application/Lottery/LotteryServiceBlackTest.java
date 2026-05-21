package com.software_project_team_15b.Ticketmaster.black.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for the {@link LotteryService} application facade.
 *
 * <p>The facade is exercised purely through its public API and observed through
 * return values and propagated exceptions. The underlying {@link ILotteryDomainService}
 * is stubbed to drive each scenario; tests do not verify call ordering, count, or
 * argument forwarding — those concerns belong to the white-box suite.
 */
@ExtendWith(MockitoExtension.class)
class LotteryServiceBlackTest {

    @Mock private ILotteryDomainService lotteryDomainService;
    @InjectMocks private LotteryService service;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    // =========================================================================
    // Lottery CRUD — positive
    // =========================================================================

    @Test
    void createEventLottery_positive_returnsNormally() {
        assertThatCode(() -> service.createEventLottery(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deleteEventLottery_positive_returnsNormally() {
        assertThatCode(() -> service.deleteEventLottery(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void addToEventLottery_positive_returnsNormally() {
        assertThatCode(() -> service.addToEventLottery(EVENT_ID, USER_A)).doesNotThrowAnyException();
    }

    // =========================================================================
    // Lottery CRUD — negative
    // =========================================================================

    @Test
    void createEventLottery_negative_propagatesIllegalArgument() {
        doThrow(new IllegalArgumentException("eventId cannot be null"))
                .when(lotteryDomainService).createEventLottery(null);

        assertThatThrownBy(() -> service.createEventLottery(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventLottery_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).deleteEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void addToEventLottery_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).addToEventLottery(EVENT_ID, USER_A);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, USER_A))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // popRandomFromEventLottery
    // =========================================================================

    @Test
    void popRandomFromEventLottery_positive_returnsDomainProvidedUser() {
        when(lotteryDomainService.popRandomFromEventLottery(EVENT_ID)).thenReturn(USER_A);

        assertThat(service.popRandomFromEventLottery(EVENT_ID)).isEqualTo(USER_A);
    }

    @Test
    void popRandomFromEventLotteryWithCount_positive_returnsDomainProvidedSet() {
        Set<UUID> expected = Set.of(USER_A, USER_B);
        when(lotteryDomainService.popRandomFromEventLottery(EVENT_ID, 2)).thenReturn(expected);

        assertThat(service.popRandomFromEventLottery(EVENT_ID, 2)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void popRandomFromEventLottery_negative_propagatesEmptyLottery() {
        doThrow(new EmptyLotteryException("empty"))
                .when(lotteryDomainService).popRandomFromEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(EmptyLotteryException.class);
    }

    @Test
    void popRandomFromEventLottery_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).popRandomFromEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void popRandomFromEventLotteryWithCount_negative_propagatesIllegalArgument() {
        doThrow(new IllegalArgumentException("count cannot be negative"))
                .when(lotteryDomainService).popRandomFromEventLottery(EVENT_ID, -1);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // runEventLottery
    // =========================================================================

    @Test
    void runEventLottery_positive_returnsDomainProvidedWinners() {
        Set<UUID> expected = Set.of(USER_A);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1)).thenReturn(expected);

        assertThat(service.runEventLottery(EVENT_ID, 1)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void runEventLottery_positive_returnsEmptySetWhenDomainSaysEmptyPool() {
        when(lotteryDomainService.runEventLottery(EVENT_ID, 5)).thenReturn(Set.of());

        assertThat(service.runEventLottery(EVENT_ID, 5)).isEmpty();
    }

    @Test
    void runEventLottery_negative_propagatesLotteryAlreadyDrawn() {
        doThrow(new LotteryAlreadyDrawnException("drawn"))
                .when(lotteryDomainService).runEventLottery(EVENT_ID, 1);

        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1))
                .isInstanceOf(LotteryAlreadyDrawnException.class);
    }

    @Test
    void runEventLottery_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).runEventLottery(EVENT_ID, 1);

        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_positive_returnsTrue_whenDomainReportsAdmitted() {
        when(lotteryDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(true);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsFalse_whenDomainReportsNotAdmitted() {
        when(lotteryDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(false);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_negative_propagatesInvalidToken() {
        doThrow(new InvalidTokenException("bad"))
                .when(lotteryDomainService).hasAccess("bad", EVENT_ID);

        assertThatThrownBy(() -> service.hasAccess("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // getEventLotteryWinners
    // =========================================================================

    @Test
    void getEventLotteryWinners_positive_returnsDomainProvidedSet() {
        Set<UUID> expected = Set.of(USER_A, USER_B);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(expected);

        assertThat(service.getEventLotteryWinners(EVENT_ID)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void getEventLotteryWinners_positive_returnsEmptySetWhenNobodyDrawn() {
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(Set.of());

        assertThat(service.getEventLotteryWinners(EVENT_ID)).isEmpty();
    }

    @Test
    void getEventLotteryWinners_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).getEventLotteryWinners(EVENT_ID);

        assertThatThrownBy(() -> service.getEventLotteryWinners(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // clearEventLotteryWinners
    // =========================================================================

    @Test
    void clearEventLotteryWinners_positive_returnsNormally() {
        assertThatCode(() -> service.clearEventLotteryWinners(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void clearEventLotteryWinners_negative_propagatesLotteryNotFound() {
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).clearEventLotteryWinners(EVENT_ID);

        assertThatThrownBy(() -> service.clearEventLotteryWinners(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_positive_returnsNoLotteryRequiredFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsNotSelectedFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.NOT_SELECTED);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsWonAndAccessValidFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsAccessExpiredFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.ACCESS_EXPIRED);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.ACCESS_EXPIRED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_negative_propagatesIllegalArgument() {
        doThrow(new IllegalArgumentException("userId cannot be null"))
                .when(lotteryDomainService).getLotteryEligibilityForEvent(null, EVENT_ID);

        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Concurrency — facade is stateless, concurrent reads return consistent results
    // =========================================================================

    @Test
    void concurrentHasAccess_allThreadsReturnSameTrueResult() throws InterruptedException {
        when(lotteryDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(true);

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (service.hasAccess("token-a", EVENT_ID)) trueCount.incrementAndGet();
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
    void concurrentRunEventLottery_allThreadsReceiveDomainProvidedWinners() throws InterruptedException {
        Set<UUID> expected = Set.of(USER_A);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1)).thenReturn(expected);

        int threads = 25;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger gotWinners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    Set<UUID> winners = service.runEventLottery(EVENT_ID, 1);
                    if (winners.equals(expected)) gotWinners.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(gotWinners.get()).isEqualTo(threads);
    }
}
