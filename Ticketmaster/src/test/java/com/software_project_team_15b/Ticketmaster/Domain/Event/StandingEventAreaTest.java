package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandingEventAreaTest {

    private StandingEventArea area(int capacity) {
        return new StandingEventArea(UUID.randomUUID(), "Floor", Money.of("20.00", "USD"), capacity);
    }

    @Test
    void constructor_rejects_zero_capacity() {
        assertThatThrownBy(() -> area(0))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void constructor_rejects_negative_capacity() {
        assertThatThrownBy(() -> area(-1))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void new_area_has_full_available_capacity() {
        assertThat(area(10).availableCapacity()).isEqualTo(10);
    }

    @Test
    void hold_decrements_available_capacity() {
        StandingEventArea a = area(10);
        a.hold(4, UUID.randomUUID());
        assertThat(a.availableCapacity()).isEqualTo(6);
    }

    @Test
    void hold_multiple_tokens_reduces_available_cumulatively() {
        StandingEventArea a = area(10);
        a.hold(3, UUID.randomUUID());
        a.hold(4, UUID.randomUUID());
        assertThat(a.availableCapacity()).isEqualTo(3);
    }

    @Test
    void hold_zero_quantity_throws() {
        assertThatThrownBy(() -> area(5).hold(0, UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void hold_negative_quantity_throws() {
        assertThatThrownBy(() -> area(5).hold(-1, UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void hold_insufficient_capacity_throws() {
        StandingEventArea a = area(2);
        assertThatThrownBy(() -> a.hold(3, UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void hold_duplicate_token_throws() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        a.hold(2, token);
        assertThatThrownBy(() -> a.hold(1, token))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void hold_exactly_full_capacity_succeeds() {
        StandingEventArea a = area(5);
        a.hold(5, UUID.randomUUID());
        assertThat(a.availableCapacity()).isZero();
    }

    @Test
    void hold_returns_receipt_with_correct_token_and_quantity() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        StandingHold hold = a.hold(3, token);
        assertThat(hold.token()).isEqualTo(token);
        assertThat(hold.quantity()).isEqualTo(3);
    }

    @Test
    void releaseByToken_restores_capacity() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        a.hold(4, token);
        boolean released = a.releaseByToken(token);
        assertThat(released).isTrue();
        assertThat(a.availableCapacity()).isEqualTo(10);
    }

    @Test
    void releaseByToken_only_removes_matching_token() {
        StandingEventArea a = area(10);
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        a.hold(3, tokenA);
        a.hold(2, tokenB);
        a.releaseByToken(tokenA);
        assertThat(a.availableCapacity()).isEqualTo(8);
        assertThat(a.activeHeldQuantity()).isEqualTo(2);
    }

    @Test
    void releaseByToken_returns_false_for_unknown_token() {
        StandingEventArea a = area(5);
        assertThat(a.releaseByToken(UUID.randomUUID())).isFalse();
    }

    @Test
    void confirmByToken_increments_soldCount() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        a.hold(4, token);
        a.confirmByToken(token);
        assertThat(a.soldCount()).isEqualTo(4);
        assertThat(a.activeHeldQuantity()).isZero();
    }

    @Test
    void confirmByToken_removes_hold_from_active_holds() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        a.hold(3, token);
        a.confirmByToken(token);
        assertThat(a.hasActiveHolds()).isFalse();
    }

    @Test
    void confirmByToken_no_hold_throws() {
        assertThatThrownBy(() -> area(5).confirmByToken(UUID.randomUUID()))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void availableCapacity_accounts_for_sold_and_held() {
        StandingEventArea a = area(10);
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        a.hold(3, tokenA);
        a.hold(2, tokenB);
        a.confirmByToken(tokenA);
        // 3 sold, 2 held → 5 available
        assertThat(a.availableCapacity()).isEqualTo(5);
    }

    @Test
    void activeHeldQuantity_sums_all_active_holds() {
        StandingEventArea a = area(20);
        a.hold(3, UUID.randomUUID());
        a.hold(5, UUID.randomUUID());
        assertThat(a.activeHeldQuantity()).isEqualTo(8);
    }

    @Test
    void quantityFor_returns_correct_quantity() {
        StandingEventArea a = area(10);
        UUID token = UUID.randomUUID();
        a.hold(4, token);
        assertThat(a.quantityFor(token)).isEqualTo(4);
    }

    @Test
    void quantityFor_returns_zero_for_unknown_token() {
        StandingEventArea a = area(10);
        assertThat(a.quantityFor(UUID.randomUUID())).isZero();
    }

    @Test
    void hasActiveHolds_true_when_hold_exists() {
        StandingEventArea a = area(5);
        a.hold(2, UUID.randomUUID());
        assertThat(a.hasActiveHolds()).isTrue();
    }

    @Test
    void hasActiveHolds_false_initially() {
        assertThat(area(5).hasActiveHolds()).isFalse();
    }

    @Test
    void hasActiveHolds_false_after_release() {
        StandingEventArea a = area(5);
        UUID token = UUID.randomUUID();
        a.hold(2, token);
        a.releaseByToken(token);
        assertThat(a.hasActiveHolds()).isFalse();
    }
}
