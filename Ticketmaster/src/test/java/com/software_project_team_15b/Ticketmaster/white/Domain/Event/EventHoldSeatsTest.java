package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventHoldSeatsTest {

    @Test
    void GivenAvailableSeats_WhenHoldSeats_ThenAllAreHeldAtomically() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();

        HoldReceipt receipt = event.holdSeats(area.areaId(), ids, UUID.randomUUID());

        assertThat(receipt.seatIds()).hasSize(3);
        assertThat(area.heldCount()).isEqualTo(3);
    }

    @Test
    void GivenOneSeatTaken_WhenHoldSeatsForAll_ThenThrowsAndOtherSeatsStayAvailable() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();
        UUID firstToken = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(ids.get(0)), firstToken);

        UUID secondToken = UUID.randomUUID();
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), ids, secondToken))
                .isInstanceOf(SeatUnavailableException.class);

        assertThat(area.heldCount()).isEqualTo(1);
    }

    @Test
    void GivenCancelledEvent_WhenHoldSeats_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();
        UUID seatId = area.seats().keySet().iterator().next();
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(seatId), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenDraftEvent_WhenHoldSeats_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.draft();
        event.addArea(area);
        UUID seatId = area.seats().keySet().iterator().next();
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(seatId), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenHeldSeats_WhenConfirmHold_ThenAllHeldSeatsBecomeSold() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "25.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), ids, token);

        ConfirmationReceipt receipt = event.confirmHold(token);

        assertThat(receipt.quantity()).isEqualTo(2);
        assertThat(area.soldCount()).isEqualTo(2);
    }

    @Test
    void GivenHeldSeats_WhenReleaseHold_ThenSeatsReturnToAvailable() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "25.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), ids, token);
        event.releaseHold(token);
        assertThat(area.availableCapacity()).isEqualTo(2);
    }

    @Test
    void GivenReleasedSeat_WhenHeldAgainByDifferentToken_ThenHoldSucceeds() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID firstToken = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), firstToken);
        event.releaseHold(firstToken);
        HoldReceipt second = event.holdSeats(area.areaId(), List.of(seatId), UUID.randomUUID());
        assertThat(second.seatIds()).containsExactly(seatId);
    }

    @Test
    void GivenNoActiveHoldForToken_WhenConfirmHold_ThenThrowsHoldNotFound() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.confirmHold(UUID.randomUUID()))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void GivenStandingAreaWithCapacity_WhenHoldStanding_ThenAvailableCapacityDecreases() {
        StandingEventArea area = EventTestFixtures.standingArea(5, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        event.holdStanding(area.areaId(), 3, UUID.randomUUID());
        assertThat(area.availableCapacity()).isEqualTo(2);
    }

    @Test
    void GivenStandingAreaWithInsufficientCapacity_WhenHoldStanding_ThenThrowsSeatUnavailable() {
        StandingEventArea area = EventTestFixtures.standingArea(2, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        assertThatThrownBy(() -> event.holdStanding(area.areaId(), 3, UUID.randomUUID()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void GivenDraftEventWithoutAreas_WhenPublish_ThenThrowsInvalidEventState() {
        Event event = EventTestFixtures.draft();
        assertThatThrownBy(event::publish).isInstanceOf(InvalidEventStateException.class);
    }
}
