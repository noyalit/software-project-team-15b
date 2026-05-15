package com.software_project_team_15b.Ticketmaster.black.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LotteryServiceBlackTest {

    @Mock private ILotteryRepository lotteryRepository;
    @Mock private IAuth auth;
    @InjectMocks private LotteryService service;

    @BeforeEach
    void injectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_C   = UUID.fromString("00000000-0000-0000-0000-000000000004");

    // =========================================================================
    // Lottery CRUD — behavior tests
    // =========================================================================

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
    void popRandomFromEventLottery_withCountLargerThanLotterySize_returnsAllEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.popRandomFromEventLottery(EVENT_ID, 10);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    void popRandomFromEventLottery_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void popRandomFromEventLottery_withCount_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.popRandomFromEventLottery(EVENT_ID, 2))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // runEventLottery — behavior tests
    // =========================================================================

    @Test
    void runEventLottery_returnsSelectedWinners() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.add(USER_C);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.runEventLottery(EVENT_ID, 2);

        assertThat(result).hasSize(2);
        assertThat(Set.of(USER_A, USER_B, USER_C)).containsAll(result);
    }

    @Test
    void runEventLottery_winnersHaveActiveAccess() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void runEventLottery_loserHasLostStatus() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> winners = service.runEventLottery(EVENT_ID, 1);
        UUID loser = Set.of(USER_A, USER_B).stream()
                .filter(u -> !winners.contains(u))
                .findFirst().orElseThrow();

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(loser, EVENT_ID);
        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void runEventLottery_emptyPool_returnsEmptySetAndMarksAsDrawn() {
        Lottery lottery = new Lottery(EVENT_ID);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.runEventLottery(EVENT_ID, 5);

        assertThat(result).isEmpty();
        LotteryEligibilityDTO view = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);
        assertThat(view.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
    }

    @Test
    void runEventLottery_countLargerThanPool_returnsAllEntries() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.runEventLottery(EVENT_ID, 100);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    void runEventLottery_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.runEventLottery(EVENT_ID, 1))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    // =========================================================================
    // getLotteryEligibilityForEvent — behavior tests
    // =========================================================================

    @Test
    void getLotteryEligibilityForEvent_noLottery_returnsNoLotteryStatus() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getLotteryEligibilityForEvent_lotteryExistsButNotDrawn_returnsNotSelectedStatus() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(new Lottery(EVENT_ID));

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.NOT_SELECTED);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getLotteryEligibilityForEvent_winnerWithActiveAccess_returnsWonStatus() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        service.runEventLottery(EVENT_ID, 1);

        LotteryEligibilityDTO result = service.getLotteryEligibilityForEvent(USER_A, EVENT_ID);

        assertThat(result.status()).isEqualTo(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    // =========================================================================
    // hasAccess — behavior tests
    // =========================================================================

    @Test
    void hasAccess_returnsTrueForWinnerWithActiveAccess() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.runEventLottery(EVENT_ID, 1);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_returnsFalseForNonWinner() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);
        when(auth.isTokenValid("token-b")).thenReturn(true);
        when(auth.extractUserId("token-b")).thenReturn(USER_B);

        service.runEventLottery(EVENT_ID, 1);

        assertThat(service.hasAccess("token-b", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_returnsFalseWhenNoLotteryDrawn() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.hasAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // getEventLotteryWinners — behavior tests
    // =========================================================================

    @Test
    void getEventLotteryWinners_returnsWinnersFromDomainEntity() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        lottery.add(USER_B);
        lottery.popRandom(2);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.getEventLotteryWinners(EVENT_ID);

        assertThat(result).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    void getEventLotteryWinners_emptyWhenNobodyDrawn() {
        Lottery lottery = new Lottery(EVENT_ID);
        lottery.add(USER_A);
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(lottery);

        Set<UUID> result = service.getEventLotteryWinners(EVENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getEventLotteryWinners_lotteryNotFound_throwsLotteryNotFoundException() {
        when(lotteryRepository.getLottery(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getEventLotteryWinners(EVENT_ID))
                .isInstanceOf(LotteryNotFoundException.class);
    }
}
