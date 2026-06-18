package com.software_project_team_15b.Ticketmaster.white.Application.Lottery;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

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
    @Mock private IAuth auth;
    @Mock private MemberDTO memberDTO;
    @InjectMocks private LotteryService service;

    private static final UUID EVENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String TOKEN_A  = "token-a";
    private static final LocalDateTime EXPIRY = LocalDateTime.now().plusHours(1);

    @AfterEach
    void verifyNoUnexpectedInteractions() {
        verifyNoMoreInteractions(lotteryDomainService, userDomainService, auth);
    }

    // =========================================================================
    // Single-method delegation tests — exactly one call, no extras
    // =========================================================================

    @Test
    void createEventLottery_delegates_andDoesNothingElse() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void deleteEventLottery_delegates_andDoesNothingElse() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).deleteEventLottery(EVENT_ID);
    }

    @Test
    void addToEventLottery_delegates_andDoesNothingElse() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.isMember(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        service.addToEventLottery(EVENT_ID, TOKEN_A);
        var inOrder = inOrder(auth, lotteryDomainService);
        inOrder.verify(auth).isTokenValid(TOKEN_A);
        inOrder.verify(auth).isMember(TOKEN_A);
        inOrder.verify(auth).extractUserId(TOKEN_A);
        inOrder.verify(lotteryDomainService).addToEventLottery(EVENT_ID, USER_A);
    }

    @Test
    void runEventLottery_delegates_andReturnsDomainServiceResult() {
        Set<UUID> expected = Set.of(USER_A);
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 2, EXPIRY)).thenReturn(expected);

        Set<UUID> result = service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 2, EXPIRY);

        assertThat(result).isSameAs(expected);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 2, EXPIRY);
    }

    @Test
    void getEventLotteryWinners_delegates_andReturnsDomainServiceResult() {
        Set<UUID> expected = Set.of(USER_A);
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(expected);

        Set<UUID> result = service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).getEventLotteryWinners(EVENT_ID);
    }

    @Test
    void getLotteryEligibilityForEvent_delegates_andReturnsDomainServiceResult() {
        LotteryEligibilityDTO expected = new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(lotteryDomainService.getLotteryEligibilityForEvent(USER_A, EVENT_ID)).thenReturn(expected);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID);

        assertThat(result).isSameAs(expected);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(lotteryDomainService).getLotteryEligibilityForEvent(USER_A, EVENT_ID);
    }

    // =========================================================================
    // Exception propagation — facade does NOT swallow or wrap exceptions
    // =========================================================================

    @Test
    void createEventLottery_propagatesDomainServiceException() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new IllegalArgumentException("boom")).when(lotteryDomainService).createEventLottery(EVENT_ID);

        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void runEventLottery_propagatesDomainServiceException() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        doThrow(new RuntimeException("already drawn")).when(lotteryDomainService).runEventLottery(any(), anyInt(), any());

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(RuntimeException.class);
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 1, EXPIRY);
    }

    // =========================================================================
    // Argument pass-through — verify the facade does not mutate or substitute args
    // =========================================================================

    @Test
    void addToEventLottery_forwardsExactArgumentsWithoutMutation() {
        UUID eid = UUID.randomUUID();
        String tok = "tok-xyz";
        UUID resolvedId = UUID.randomUUID();
        when(auth.isTokenValid(tok)).thenReturn(true);
        when(auth.isMember(tok)).thenReturn(true);
        when(auth.extractUserId(tok)).thenReturn(resolvedId);

        service.addToEventLottery(eid, tok);

        verify(auth).isTokenValid(tok);
        verify(auth).isMember(tok);
        verify(auth).extractUserId(tok);
        verify(lotteryDomainService).addToEventLottery(eid, resolvedId);
    }

    @Test
    void runEventLottery_forwardsCountVerbatim() {
        UUID eid = UUID.randomUUID();
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, eid)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(eid, 7, EXPIRY)).thenReturn(Set.of());

        service.runEventLottery(TOKEN_A, COMPANY_ID, eid, 7, EXPIRY);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, eid);
        verify(lotteryDomainService).runEventLottery(eid, 7, EXPIRY);
    }

    // =========================================================================
    // Concurrency — facade is stateless; concurrent delegation must not corrupt counts
    // =========================================================================

    @Test
    void concurrentDelegation_eachCallForwardsExactlyOnce() throws InterruptedException {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger completed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID);
                    completed.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();

        assertThat(completed.get()).isEqualTo(threads);
        verify(auth, times(threads)).isTokenValid(TOKEN_A);
        verify(auth, times(threads)).extractUserId(TOKEN_A);
        verify(userDomainService, times(threads)).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService, times(threads)).createEventLottery(EVENT_ID);
    }

    // =========================================================================
    // Constructor null checks
    // =========================================================================

    @Test
    void constructor_throws_when_lotteryDomainService_is_null() {
        assertThatThrownBy(() -> new LotteryService(null, userDomainService, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_userDomainService_is_null() {
        assertThatThrownBy(() -> new LotteryService(lotteryDomainService, null, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_auth_is_null() {
        assertThatThrownBy(() -> new LotteryService(lotteryDomainService, userDomainService, null))
                .isInstanceOf(NullPointerException.class);
    }

    // =========================================================================
    // requireEventPermissions — owner and founder paths
    // =========================================================================

    @Test
    void createEventLottery_succeedsWhenCallerIsOwner() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(true);

        service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void createEventLottery_succeedsWhenCallerIsFounder() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveCompanyManager(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(true);

        service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveCompanyManager(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveFounder(USER_A, COMPANY_ID);
        verify(lotteryDomainService).createEventLottery(EVENT_ID);
    }

    @Test
    void createEventLottery_throwsWhenUnauthorized() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveCompanyManager(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveCompanyManager(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveFounder(USER_A, COMPANY_ID);
    }

    // =========================================================================
    // createEventLottery — null / invalid token guards
    // =========================================================================

    @Test
    void createEventLottery_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.createEventLottery(null, COMPANY_ID, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventLottery_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEventLottery_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.createEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    // =========================================================================
    // deleteEventLottery — null / invalid token / unauthorized guards
    // =========================================================================

    @Test
    void deleteEventLottery_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.deleteEventLottery(null, COMPANY_ID, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventLottery_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventLottery_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    @Test
    void deleteEventLottery_throwsWhenUnauthorized() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveCompanyManager(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveCompanyManager(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveFounder(USER_A, COMPANY_ID);
    }

    // =========================================================================
    // addToEventLottery — null / invalid token / not-member guards
    // =========================================================================

    @Test
    void addToEventLottery_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.addToEventLottery(null, TOKEN_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addToEventLottery_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addToEventLottery_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, TOKEN_A))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    @Test
    void addToEventLottery_throwsWhenNotMember() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.isMember(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.addToEventLottery(EVENT_ID, TOKEN_A))
                .isInstanceOf(UnauthorizedException.class);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).isMember(TOKEN_A);
    }

    // =========================================================================
    // runEventLottery — argument guards
    // =========================================================================

    @Test
    void runEventLottery_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.runEventLottery(null, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, null, 1, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_throwsWhenCountIsNegative() {
        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, -1, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_throwsWhenExpirationTimeIsNull() {
        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_throwsWhenExpirationTimeIsInPast() {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, pastTime))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runEventLottery_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    @Test
    void runEventLottery_throwsWhenUnauthorized() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveCompanyManager(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.runEventLottery(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY))
                .isInstanceOf(UnauthorizedException.class);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveFounder(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveCompanyManager(USER_A, COMPANY_ID);
    }

    // =========================================================================
    // getEventLotteryWinners — null / invalid token / unauthorized guards
    // =========================================================================

    @Test
    void getEventLotteryWinners_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.getEventLotteryWinners(null, COMPANY_ID, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEventLotteryWinners_throwsWhenCompanyIdIsNull() {
        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEventLotteryWinners_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEventLotteryWinners_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    @Test
    void getEventLotteryWinners_throwsWhenUnauthorized() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(false);
        when(userDomainService.isActiveOwner(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveFounder(USER_A, COMPANY_ID)).thenReturn(false);
        when(userDomainService.isActiveCompanyManager(USER_A, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getEventLotteryWinners(TOKEN_A, COMPANY_ID, EVENT_ID))
                .isInstanceOf(UnauthorizedException.class);

        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(userDomainService).isActiveOwner(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveFounder(USER_A, COMPANY_ID);
        verify(userDomainService).isActiveCompanyManager(USER_A, COMPANY_ID);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent — null / invalid token guards
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_throwsWhenTokenIsNull() {
        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLotteryEligibilityForEvent_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(TOKEN_A, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLotteryEligibilityForEvent_throwsWhenTokenIsInvalid() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(false);

        assertThatThrownBy(() -> service.getLotteryEligibilityForEvent(TOKEN_A, EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);

        verify(auth).isTokenValid(TOKEN_A);
    }

    // =========================================================================
    // runEventLotteryUsernames — username resolution and fallback
    // =========================================================================

    @Test
    void runEventLotteryUsernames_returnsResolvedUsername() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1, EXPIRY)).thenReturn(Set.of(USER_A));
        when(userDomainService.resolveMemberById(USER_A)).thenReturn(memberDTO);
        when(memberDTO.getUsername()).thenReturn("alice");

        Set<String> result = service.runEventLotteryUsernames(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY);

        assertThat(result).containsExactly("alice");
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 1, EXPIRY);
        verify(userDomainService).resolveMemberById(USER_A);
    }

    @Test
    void runEventLotteryUsernames_fallsBackToIdStringWhenResolveFails() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.runEventLottery(EVENT_ID, 1, EXPIRY)).thenReturn(Set.of(USER_A));
        when(userDomainService.resolveMemberById(USER_A)).thenThrow(new RuntimeException("not found"));

        Set<String> result = service.runEventLotteryUsernames(TOKEN_A, COMPANY_ID, EVENT_ID, 1, EXPIRY);

        assertThat(result).containsExactly(USER_A.toString());
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).runEventLottery(EVENT_ID, 1, EXPIRY);
        verify(userDomainService).resolveMemberById(USER_A);
    }

    // =========================================================================
    // getEventLotteryWinnerUsernames — username resolution and fallback
    // =========================================================================

    @Test
    void getEventLotteryWinnerUsernames_returnsResolvedUsername() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(Set.of(USER_A));
        when(userDomainService.resolveMemberById(USER_A)).thenReturn(memberDTO);
        when(memberDTO.getUsername()).thenReturn("alice");

        Set<String> result = service.getEventLotteryWinnerUsernames(TOKEN_A, COMPANY_ID, EVENT_ID);

        assertThat(result).containsExactly("alice");
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).getEventLotteryWinners(EVENT_ID);
        verify(userDomainService).resolveMemberById(USER_A);
    }

    @Test
    void getEventLotteryWinnerUsernames_fallsBackToIdStringWhenResolveFails() {
        when(auth.isTokenValid(TOKEN_A)).thenReturn(true);
        when(auth.extractUserId(TOKEN_A)).thenReturn(USER_A);
        when(userDomainService.isActiveManager(USER_A, COMPANY_ID, EVENT_ID)).thenReturn(true);
        when(lotteryDomainService.getEventLotteryWinners(EVENT_ID)).thenReturn(Set.of(USER_A));
        when(userDomainService.resolveMemberById(USER_A)).thenThrow(new RuntimeException("not found"));

        Set<String> result = service.getEventLotteryWinnerUsernames(TOKEN_A, COMPANY_ID, EVENT_ID);

        assertThat(result).containsExactly(USER_A.toString());
        verify(auth).isTokenValid(TOKEN_A);
        verify(auth).extractUserId(TOKEN_A);
        verify(userDomainService).isActiveManager(USER_A, COMPANY_ID, EVENT_ID);
        verify(lotteryDomainService).getEventLotteryWinners(EVENT_ID);
        verify(userDomainService).resolveMemberById(USER_A);
    }
}
