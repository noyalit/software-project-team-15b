package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.published;
import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.seatingArea;
import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.standingArea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventConfirmHoldBranchesTest {

    @Test
    void GivenCancelledEvent_WhenConfirmHold_ThenThrowsInvalidEventState() {
        Event event = published(seatingArea(2, "10.00"));
        event.cancel();

        assertThatThrownBy(() -> event.confirmHold(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenUnknownHoldToken_WhenConfirmHold_ThenThrowsHoldNotFound() {
        Event event = published(seatingArea(2, "10.00"));
        assertThatThrownBy(() -> event.confirmHold(UUID.randomUUID()))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void GivenStandingHoldConfirmedWithRemainingCapacity_WhenConfirmHold_ThenStaysPublished() {
        StandingEventArea standing = standingArea(3, "10.00");
        Event event = published(new StandingEventArea[] {standing}, new SeatingEventArea[0]);

        UUID token = UUID.randomUUID();
        event.holdStanding(standing.areaId(), 1, token);
        ConfirmationReceipt receipt = event.confirmHold(token);

        assertThat(receipt.quantity()).isEqualTo(1);
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void GivenLastStandingHoldConfirmed_WhenConfirmHold_ThenTransitionsToSoldOut() {
        StandingEventArea standing = standingArea(2, "10.00");
        Event event = published(new StandingEventArea[] {standing}, new SeatingEventArea[0]);

        UUID token = UUID.randomUUID();
        event.holdStanding(standing.areaId(), 2, token);
        event.confirmHold(token);

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void GivenSeatingHoldConfirmed_WhenConfirmHold_ThenReceiptContainsSeats() {
        SeatingEventArea seating = seatingArea(3, "10.00");
        Event event = published(seating);

        UUID seat = seating.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(seating.areaId(), java.util.List.of(seat), token);

        ConfirmationReceipt receipt = event.confirmHold(token);

        assertThat(receipt.seatIds()).containsExactly(seat);
        assertThat(receipt.quantity()).isEqualTo(1);
    }

    @Test
    void GivenUnknownAreaId_WhenPriceFor_ThenThrowsInvalidEventState() {
        Event event = published(seatingArea(1, "10.00"));
        assertThatThrownBy(() -> event.priceFor(UUID.randomUUID(), 1))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenUnknownAreaId_WhenHeldCountIn_ThenThrowsInvalidEventState() {
        Event event = published(seatingArea(1, "10.00"));
        assertThatThrownBy(() -> event.heldCountIn(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }
}
