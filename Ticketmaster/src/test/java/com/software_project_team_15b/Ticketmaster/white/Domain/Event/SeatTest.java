package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatTest {

    @Test
    void GivenNullSeatId_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Seat(null, "A", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seatId");
    }

    @Test
    void GivenNullRow_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Seat(UUID.randomUUID(), null, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row");
    }

    @Test
    void GivenBlankRow_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Seat(UUID.randomUUID(), "  ", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row");
    }

    @Test
    void GivenNullNumber_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Seat(UUID.randomUUID(), "A", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
    }

    @Test
    void GivenBlankNumber_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Seat(UUID.randomUUID(), "A", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
    }

    @Test
    void GivenAvailableSeat_WhenRequireHoldable_ThenDoesNotThrow() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.requireHoldable();
        assertThat(s.status()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void GivenHeldSeat_WhenRequireHoldable_ThenThrowsSeatUnavailable() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.markHeld(UUID.randomUUID());
        assertThatThrownBy(s::requireHoldable).isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void GivenNullToken_WhenMarkHeld_ThenThrowsIllegalArgument() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markHeld(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenHeldSeat_WhenMarkHeldAgain_ThenThrowsSeatUnavailable() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.markHeld(UUID.randomUUID());
        assertThatThrownBy(() -> s.markHeld(UUID.randomUUID())).isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void GivenNullToken_WhenMarkSold_ThenThrowsIllegalArgument() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markSold(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenAvailableSeat_WhenMarkSold_ThenThrowsSeatUnavailable() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markSold(UUID.randomUUID())).isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void GivenSeatHeldByOtherToken_WhenMarkSold_ThenThrowsHoldNotFound() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        s.markHeld(UUID.randomUUID());
        assertThatThrownBy(() -> s.markSold(UUID.randomUUID())).isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void GivenNullToken_WhenMarkAvailable_ThenThrowsIllegalArgument() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markAvailable(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenAvailableSeat_WhenMarkAvailable_ThenThrowsSeatUnavailable() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThatThrownBy(() -> s.markAvailable(UUID.randomUUID())).isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void GivenSeatHeldByOtherToken_WhenMarkAvailable_ThenSilentlyNoOps() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID owner = UUID.randomUUID();
        s.markHeld(owner);
        s.markAvailable(UUID.randomUUID());
        assertThat(s.status()).isEqualTo(SeatStatus.HELD);
        assertThat(s.heldBy()).isEqualTo(owner);
    }

    @Test
    void GivenMatchingToken_WhenMarkAvailable_ThenClearsHeldByAndReturnsToAvailable() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        UUID owner = UUID.randomUUID();
        s.markHeld(owner);
        s.markAvailable(owner);
        assertThat(s.status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(s.heldBy()).isNull();
    }

    @Test
    void GivenSameSeatId_WhenEquals_ThenReturnsTrue() {
        UUID id = UUID.randomUUID();
        Seat a = new Seat(id, "A", "1");
        Seat b = new Seat(id, "B", "2");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void GivenSameInstance_WhenEquals_ThenReturnsTrue() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThat(s.equals(s)).isTrue();
    }

    @Test
    void GivenDifferentSeatId_WhenEquals_ThenReturnsFalse() {
        Seat a = new Seat(UUID.randomUUID(), "A", "1");
        Seat b = new Seat(UUID.randomUUID(), "A", "1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void GivenNonSeatObject_WhenEquals_ThenReturnsFalse() {
        Seat s = new Seat(UUID.randomUUID(), "A", "1");
        assertThat(s.equals("not a seat")).isFalse();
        assertThat(s.equals(null)).isFalse();
    }

    @Test
    void GivenSameSeatId_WhenHashCode_ThenIsEqual() {
        UUID id = UUID.randomUUID();
        Seat a = new Seat(id, "A", "1");
        Seat b = new Seat(id, "Z", "9");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
