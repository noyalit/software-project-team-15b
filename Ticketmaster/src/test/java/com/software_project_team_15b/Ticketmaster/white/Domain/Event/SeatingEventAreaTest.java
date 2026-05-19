package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeatingEventAreaTest {

    private SeatingEventArea area;
    private Seat seatA1;
    private Seat seatA2;
    private Seat seatA3;

    @BeforeEach
    void setUp() {
        area = new SeatingEventArea(UUID.randomUUID(), "Main Hall", Money.of("50.00", "USD"));
        seatA1 = new Seat(UUID.randomUUID(), "A", "1");
        seatA2 = new Seat(UUID.randomUUID(), "A", "2");
        seatA3 = new Seat(UUID.randomUUID(), "A", "3");
    }

    @Test
    void new_area_has_zero_counts() {
        assertThat(area.totalSeats()).isZero();
        assertThat(area.availableCapacity()).isZero();
        assertThat(area.heldCount()).isZero();
        assertThat(area.soldCount()).isZero();
    }

    @Test
    void addSeat_increases_counts() {
        area.addSeat(seatA1);
        assertThat(area.totalSeats()).isEqualTo(1);
        assertThat(area.availableCapacity()).isEqualTo(1);
    }

    @Test
    void addSeat_duplicate_throws() {
        area.addSeat(seatA1);
        assertThatThrownBy(() -> area.addSeat(seatA1))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void requireSeat_returns_known_seat() {
        area.addSeat(seatA1);
        Seat found = area.requireSeat(seatA1.seatId());
        assertThat(found.seatId()).isEqualTo(seatA1.seatId());
    }

    @Test
    void requireSeat_unknown_id_throws() {
        assertThatThrownBy(() -> area.requireSeat(UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeats_marks_seats_held_and_decrements_available() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        UUID token = UUID.randomUUID();

        area.holdSeats(List.of(seatA1.seatId(), seatA2.seatId()), token);

        assertThat(area.heldCount()).isEqualTo(2);
        assertThat(area.availableCapacity()).isZero();
    }

    @Test
    void holdSeats_fails_atomically_when_one_seat_unavailable() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        area.holdSeats(List.of(seatA1.seatId()), UUID.randomUUID());

        assertThatThrownBy(() -> area.holdSeats(List.of(seatA1.seatId(), seatA2.seatId()), UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);

        assertThat(area.heldCount()).isEqualTo(1);
        assertThat(area.availableCapacity()).isEqualTo(1);
    }

    @Test
    void holdSeats_unknown_seat_throws() {
        assertThatThrownBy(() -> area.holdSeats(List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void releaseByToken_returns_seats_to_available() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        UUID token = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId(), seatA2.seatId()), token);

        boolean released = area.releaseByToken(token);

        assertThat(released).isTrue();
        assertThat(area.availableCapacity()).isEqualTo(2);
        assertThat(area.heldCount()).isZero();
    }

    @Test
    void releaseByToken_only_releases_matching_token() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId()), tokenA);
        area.holdSeats(List.of(seatA2.seatId()), tokenB);

        area.releaseByToken(tokenA);

        assertThat(area.heldCount()).isEqualTo(1);
        assertThat(seatA2.status()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void releaseByToken_returns_false_for_unknown_token() {
        area.addSeat(seatA1);
        boolean released = area.releaseByToken(UUID.randomUUID());
        assertThat(released).isFalse();
    }

    @Test
    void confirmByToken_marks_seats_sold() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        UUID token = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId(), seatA2.seatId()), token);

        area.confirmByToken(token);

        assertThat(area.soldCount()).isEqualTo(2);
        assertThat(area.heldCount()).isZero();
    }

    @Test
    void confirmByToken_no_hold_throws() {
        assertThatThrownBy(() -> area.confirmByToken(UUID.randomUUID()))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void seatIdsHeldBy_returns_only_matching_token_ids() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        area.addSeat(seatA3);
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId(), seatA2.seatId()), tokenA);
        area.holdSeats(List.of(seatA3.seatId()), tokenB);

        List<UUID> ids = area.seatIdsHeldBy(tokenA);

        assertThat(ids).containsExactlyInAnyOrder(seatA1.seatId(), seatA2.seatId());
    }

    @Test
    void seatIdsHeldBy_returns_empty_for_unknown_token() {
        area.addSeat(seatA1);
        assertThat(area.seatIdsHeldBy(UUID.randomUUID())).isEmpty();
    }

    @Test
    void hasActiveHolds_true_when_seat_held() {
        area.addSeat(seatA1);
        area.holdSeats(List.of(seatA1.seatId()), UUID.randomUUID());
        assertThat(area.hasActiveHolds()).isTrue();
    }

    @Test
    void hasActiveHolds_false_when_no_holds() {
        area.addSeat(seatA1);
        assertThat(area.hasActiveHolds()).isFalse();
    }

    @Test
    void hasActiveHolds_false_after_all_released() {
        area.addSeat(seatA1);
        UUID token = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId()), token);
        area.releaseByToken(token);
        assertThat(area.hasActiveHolds()).isFalse();
    }

    @Test
    void availableCapacity_excludes_held_and_sold() {
        area.addSeat(seatA1);
        area.addSeat(seatA2);
        area.addSeat(seatA3);
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId()), tokenA);
        area.holdSeats(List.of(seatA2.seatId()), tokenB);
        area.confirmByToken(tokenA);

        assertThat(area.availableCapacity()).isEqualTo(1);
        assertThat(area.heldCount()).isEqualTo(1);
        assertThat(area.soldCount()).isEqualTo(1);
    }

    // --- null-safety ---

    @Test
    void addSeat_null_throws() {
        assertThatThrownBy(() -> area.addSeat(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void holdSeats_null_seatIds_throws() {
        assertThatThrownBy(() -> area.holdSeats(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void holdSeats_null_token_throws() {
        area.addSeat(seatA1);
        assertThatThrownBy(() -> area.holdSeats(List.of(seatA1.seatId()), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void releaseByToken_null_throws() {
        assertThatThrownBy(() -> area.releaseByToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmByToken_null_throws() {
        assertThatThrownBy(() -> area.confirmByToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seatIdsHeldBy_null_throws() {
        assertThatThrownBy(() -> area.seatIdsHeldBy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- constructor validation ---

    @Test
    void constructor_null_areaId_throws() {
        assertThatThrownBy(() -> new SeatingEventArea(null, "Hall", Money.of("10.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_null_name_throws() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), null, Money.of("10.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blank_name_throws() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), "  ", Money.of("10.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_null_basePrice_throws() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), "Hall", null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- edge cases ---

    @Test
    void holdSeats_empty_list_is_no_op() {
        area.addSeat(seatA1);
        List<Seat> result = area.holdSeats(List.of(), UUID.randomUUID());
        assertThat(result).isEmpty();
        assertThat(area.heldCount()).isZero();
        assertThat(area.availableCapacity()).isEqualTo(1);
    }

    @Test
    void seatIdsHeldBy_excludes_sold_seats() {
        // regression: Seat.markSold does not clear heldBy, so filter must check status
        area.addSeat(seatA1);
        UUID token = UUID.randomUUID();
        area.holdSeats(List.of(seatA1.seatId()), token);
        area.confirmByToken(token);

        assertThat(area.seatIdsHeldBy(token)).isEmpty();
    }

    @Test
    void seats_map_is_unmodifiable() {
        area.addSeat(seatA1);
        assertThatThrownBy(() -> area.seats().put(UUID.randomUUID(), seatA2))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
