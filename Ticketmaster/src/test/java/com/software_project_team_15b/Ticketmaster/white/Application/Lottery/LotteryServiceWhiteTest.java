package com.software_project_team_15b.Ticketmaster.white.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * White-box tests for the {@link LotteryService} application facade.
 *
 * <p>These tests verify the wiring contract of the facade: every public method
 * must forward its arguments to the same-named method on the injected
 * {@link ILotteryDomainService}, and the facade must not perform any work beyond
 * that delegation. After each test the mock is checked for {@code noMoreInteractions}
 * to guard against accidental side effects.
 */
@ExtendWith(MockitoExtension.class)
class LotteryServiceWhiteTest {

    @Mock private ILotteryDomainService lotteryDomainService;
    @Mock private UserDomainService userDomainService;
    @InjectMocks private LotteryService service;

    private static final UUID EVENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @AfterEach
    void verifyNoUnexpectedInteractions() {
        verifyNoMoreInteractions(lotteryDomainService, userDomainService);
    }

    // =========================================================================
    // Single-method delegation tests — exactly one call, no extras
    // =========================================================================

    @Test
    void createEventLottery_delegates_andDoesNothingElse() {
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.createEventLottery(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void deleteEventLottery_delegates_andDoesNothingElse() {
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.deleteEventLottery(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).deleteEventLottery(EVENT_ID);
    }

    @Test
    void addToEventLottery_delegates_andDoesNothingElse() {
        service.addToEventLottery(EVENT_ID, USER_A);
        verify(lotteryDomainService).addToEventLottery(EVENT_ID, USER_A);
    }

    @Test
    void runEventLottery_delegates_andReturnsDomainServiceResult() {
        Set<UUID> expected = Set.of(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 2)).thenReturn(expected);

        Set<UUID> result = service.runEventLottery(USER_A, COMPANY_ID, EVENT_ID, 2);

        assertThat(result).isSameAs(expected);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 2);
    }

    @Test
    void getEventLotteryWinners_delegates_andReturnsDomainServiceResult() {
        Set<UUID> expected = Set.of(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(expected);

        Set<UUID> result = service.getEventLotteryWinners(USER_A, COMPANY_ID, EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).getEventLotteryWinners(EVENT_ID);
    }

    @Test
    void getLotteryEligibilityForEvent_delegates_andReturnsDomainServiceResult() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(lotteryDomainService).getLotteryEligibilityForEvent(USER_A, EVENT_ID);
    }

    // =========================================================================
    // Exception propagation — facade does NOT swallow or wrap exceptions
    // =========================================================================

    @Test
    void createEventLottery_propagatesDomainServiceException() {
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new IllegalArgumentException("boom")).when(lotteryDomainService).createEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.createEventLottery(USER_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void runEventLottery_propagatesDomainServiceException() {
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new RuntimeException("already drawn")).when(lotteryDomainService).runEventLottery(any(), anyInt());

        assertThatThrownBy(() -> service.runEventLottery(USER_A, COMPANY_ID, EVENT_ID, 1))
                .isInstanceOf(RuntimeException.class);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 1);
    }

    // =========================================================================
    // Argument pass-through — verify the facade does not mutate or substitute args
    // =========================================================================

    @Test
    void addToEventLottery_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();

        service.addToEventLottery(eid, uid);

        verify(lotteryDomainService).addToEventLottery(eid, uid);
    }

    @Test
    void runEventLottery_forwardsCountVerbatim() {
        UUID eid = UUID.randomUUID();
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, eid)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(eid, 7)).thenReturn(Set.of());

        service.runEventLottery(USER_A, COMPANY_ID, eid, 7);

        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, eid);
        verify(lotteryDomainService).runEventLottery(eid, 7);
    }

    // =========================================================================
    // Concurrency — facade is stateless; concurrent delegation must not corrupt counts
    // =========================================================================

    @Test
    void concurrentDelegation_eachCallForwardsExactlyOnce() throws InterruptedException {
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger completed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.createEventLottery(USER_A, COMPANY_ID, EVENT_ID);
                    completed.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(completed.get()).isEqualTo(threads);
        verify(userDomainService, times(threads)).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService, times(threads)).createEventLottery(EVENT_ID);
    }
}