package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventStandingResizeTest {

    private StandingEventArea area(int capacity) {
        return new StandingEventArea(UUID.randomUUID(), "Floor", usd("10.00"), capacity);
    }

    @Test
    void GivenResizeBelowOne_WhenResizeTo_ThenThrowsInvalidEventState() {
        StandingEventArea a = area(5);
        assertThatThrownBy(() -> a.resizeTo(0)).isInstanceOf(InvalidEventStateException.class);
        assertThatThrownBy(() -> a.resizeTo(-3)).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenResizeBelowSoldPlusHeldFloor_WhenResizeTo_ThenThrowsInvalidEventState() {
        StandingEventArea a = area(5);
        UUID holdToken = UUID.randomUUID();
        a.hold(3, holdToken);

        assertThatThrownBy(() -> a.resizeTo(2)).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenResizeEqualToHeldFloor_WhenResizeTo_ThenShrinksToFloor() {
        StandingEventArea a = area(6);
        a.hold(2, UUID.randomUUID());

        a.resizeTo(2);

        assertThat(a.capacity()).isEqualTo(2);
        assertThat(a.availableCapacity()).isZero();
    }

    @Test
    void GivenResizeEqualToCurrent_WhenResizeTo_ThenIsNoOp() {
        StandingEventArea a = area(4);
        a.resizeTo(4);
        assertThat(a.capacity()).isEqualTo(4);
        assertThat(a.availableCapacity()).isEqualTo(4);
    }

    @Test
    void GivenGrowCapacity_WhenResizeTo_ThenAddsAvailableSeats() {
        StandingEventArea a = area(2);
        a.resizeTo(5);
        assertThat(a.capacity()).isEqualTo(5);
        assertThat(a.availableCapacity()).isEqualTo(5);
    }

    @Test
    void GivenShrinkWithNoHolds_WhenResizeTo_ThenRemovesAvailableSeatsOnly() {
        StandingEventArea a = area(6);
        a.resizeTo(2);
        assertThat(a.capacity()).isEqualTo(2);
        assertThat(a.availableCapacity()).isEqualTo(2);
    }

    @Test
    void GivenSomeHeldSeats_WhenShrinkAboveFloor_ThenPreservesHeldAndTrimsAvailable() {
        StandingEventArea a = area(6);
        UUID token = UUID.randomUUID();
        a.hold(2, token);

        a.resizeTo(4);

        assertThat(a.capacity()).isEqualTo(4);
        assertThat(a.activeHeldQuantity()).isEqualTo(2);
        assertThat(a.availableCapacity()).isEqualTo(2);
    }
}
