package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatStateMachineTest {

    @Test
    void new_seat_is_available() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThat(s.status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(s.isHoldable()).isTrue();
    }

    @Test
    void available_to_held_transition() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID token = UUID.randomUUID();
        s.markHeld(token);
        assertThat(s.status()).isEqualTo(SeatStatus.HELD);
        assertThat(s.heldBy()).isEqualTo(token);
    }

    @Test
    void held_to_sold_transition() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID token = UUID.randomUUID();
        s.markHeld(token);
        s.markSold(token);
        assertThat(s.status()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void confirm_rejects_wrong_token() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.markHeld(UUID.randomUUID());
        assertThatThrownBy(() -> s.markSold(UUID.randomUUID()))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void confirm_rejects_when_available() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markSold(UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void release_returns_to_available() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID token = UUID.randomUUID();
        s.markHeld(token);
        s.markAvailable(token);
        assertThat(s.status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(s.heldBy()).isNull();
    }

    @Test
    void release_ignores_wrong_token() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID token = UUID.randomUUID();
        s.markHeld(token);
        s.markAvailable(UUID.randomUUID());
        assertThat(s.status()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void requireHoldable_throws_for_sold_seat() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID token = UUID.randomUUID();
        s.markHeld(token);
        s.markSold(token);
        assertThatThrownBy(s::requireHoldable)
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void requireHoldable_throws_for_held_seat() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.markHeld(UUID.randomUUID());
        assertThatThrownBy(s::requireHoldable)
                .isInstanceOf(SeatUnavailableException.class);
    }
}
