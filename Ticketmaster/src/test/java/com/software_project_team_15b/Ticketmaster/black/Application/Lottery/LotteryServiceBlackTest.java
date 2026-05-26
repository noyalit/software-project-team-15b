package com.software_project_team_15b.Ticketmaster.black.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

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
    @Mock private UserDomainService userDomainService;
    @Mock private IAuth auth;
    @InjectMocks private LotteryService service;

    private static final UUID EVENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String TOKEN_A  = "token-a";
    private static final LocalDateTime EXPIRY = LocalDateTime.now().plusHours(1);

    // =========================================================================
    // Lottery CRUD — positive
    // =========================================================================

    @Test
    void createEventLottery_positive_returnsNormally() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        assertThatCode(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deleteEventLottery_positive_returnsNormally() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        assertThatCode(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void addToEventLottery_positive_returnsNormally() {
        when(auth.isMember(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        assertThatCode(() -> service.addToEventLottery(EVENT_ID, TOKEN_A)).doesNotThrowAnyException();
    }

    // =========================================================================
    // Lottery CRUD — negative
    // =========================================================================

    @Test
    void createEventLottery_negative_propagatesIllegalArgument() {
        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventLottery_negative_userNotAuthorized_throwsUnauthorized() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deleteEventLottery_negative_propagatesLotteryNotFound() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).deleteEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void deleteEventLottery_negative_userNotAuthorized_throwsUnauthorized() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void addToEventLottery_negative_userNotMember_throwsUnauthorized() {
        when(auth.isMember(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, TOKEN_A))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void addToEventLottery_negative_propagatesLotteryNotFound() {
        when(auth.isMember(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).addToEventLottery(EVENT_ID, USER_A);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, TOKEN_A))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // runEventLottery
    // =========================================================================

    @Test
    void runEventLottery_positive_returnsDomainProvidedWinners() {
        Set<UUID> expected = Set.of(USER_A);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1, EXPIRY)).thenReturn(expected);

        assertThat(service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void runEventLottery_positive_returnsEmptySetWhenDomainSaysEmptyPool() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 5, EXPIRY)).thenReturn(Set.of());

        assertThat(service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 5, EXPIRY)).isEmpty();
    }

    @Test
    void runEventLottery_negative_userNotAuthorized_throwsUnauthorized() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void runEventLottery_negative_propagatesLotteryAlreadyDrawn() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new LotteryAlreadyDrawnException("drawn"))
                .when(lotteryDomainService).runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(LotteryAlreadyDrawnException.class);
    }

    @Test
    void runEventLottery_negative_propagatesLotteryNotFound() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).runEventLottery(EVENT_ID, 1, EXPIRY);

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // getEventLotteryWinners
    // =========================================================================

    @Test
    void getEventLotteryWinners_positive_returnsDomainProvidedSet() {
        Set<UUID> expected = Set.of(USER_A, USER_B);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(expected);

        assertThat(service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void getEventLotteryWinners_positive_returnsEmptySetWhenNobodyDrawn() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(Set.of());

        assertThat(service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID)).isEmpty();
    }

    @Test
    void getEventLotteryWinners_negative_userNotAuthorized_throwsUnauthorized() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getEventLotteryWinners_negative_propagatesLotteryNotFound() {
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new LotteryNotFoundException("missing"))
                .when(lotteryDomainService).getEventLotteryWinners(EVENT_ID);

        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_positive_returnsNoLotteryRequiredFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsNotSelectedFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.NOT_SELECTED);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsWonAndAccessValidFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_positive_returnsAccessExpiredFromDomain() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.ACCESS_EXPIRED);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.ACCESS_EXPIRED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_negative_propagatesIllegalArgument() {
        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Concurrency — facade is stateless, concurrent reads return consistent results
    // =========================================================================

    @Test
    void concurrentRunEventLottery_allThreadsReceiveDomainProvidedWinners() throws InterruptedException {
        Set<UUID> expected = Set.of(USER_A);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1, EXPIRY)).thenReturn(expected);

        int threads = 25;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger gotWinners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    Set<UUID> winners = service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY);
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
