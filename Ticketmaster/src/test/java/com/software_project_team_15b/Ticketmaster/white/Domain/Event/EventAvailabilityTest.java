package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventAvailabilityTest {

    @Test
    void GivenPublishedEventWithAvailableSeatingArea_WhenBookingStatus_ThenReturnsAvailable() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void GivenPublishedEventWithAvailableStandingArea_WhenBookingStatus_ThenReturnsAvailable() {
        StandingEventArea area = EventTestFixtures.standingArea(100, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void GivenDraftEvent_WhenBookingStatus_ThenReturnsInactive() {
        Event event = EventTestFixtures.draft();
        event.addArea(EventTestFixtures.seatingArea(1, "10.00"));

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void GivenCancelledEvent_WhenBookingStatus_ThenReturnsInactive() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "30.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void GivenAllSeatsHeldAndConfirmed_WhenBookingStatus_ThenReturnsSoldOut() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.confirmHold(token);

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.SOLD_OUT);
    }

    @Test
    void GivenAllSeatsHeldNotConfirmed_WhenBookingStatus_ThenReturnsSoldOutButStatusPublished() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        event.holdSeats(area.areaId(), all, UUID.randomUUID());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.SOLD_OUT);
    }

    @Test
    void GivenPastDatedEvent_WhenBookingStatus_ThenReturnsInactive() {
        Event event = new Event(
                UUID.randomUUID(), UUID.randomUUID(), "Past Show", "Artist",
                Category.CONCERT, Instant.now().minusSeconds(3600), "Venue",
                List.of(),
                List.of()
        );
        event.addArea(EventTestFixtures.seatingArea(1, "10.00"));
        event.publish();

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void GivenFullSeatingButStandingStillHasCapacity_WhenBookingStatus_ThenReturnsAvailable() {
        SeatingEventArea seating = EventTestFixtures.seatingArea(2, "50.00");
        StandingEventArea standing = EventTestFixtures.standingArea(10, "20.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{standing}, new SeatingEventArea[]{seating});
        List<UUID> seatIds = seating.seats().keySet().stream().toList();
        event.holdSeats(seating.areaId(), seatIds, UUID.randomUUID());

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }
}
