package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.DelegatingEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.DelegatingEventPurchasePolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventAvailabilityTest {

    @Test
    void published_event_with_available_seating_is_available() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void published_event_with_available_standing_is_available() {
        StandingEventArea area = EventTestFixtures.standingArea(100, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void draft_event_is_inactive() {
        Event event = EventTestFixtures.draft();
        event.addArea(EventTestFixtures.seatingArea(1, "10.00"));

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void cancelled_event_is_inactive() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "30.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void sold_out_status_event_returns_sold_out() {
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
    void published_event_with_all_seats_held_but_not_confirmed_is_sold_out() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        event.holdSeats(area.areaId(), all, UUID.randomUUID());

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.SOLD_OUT);
    }

    @Test
    void past_event_is_inactive() {
        Event event = new Event(
                UUID.randomUUID(), UUID.randomUUID(), "Past Show", "Artist",
                Category.CONCERT, Instant.now().minusSeconds(3600), "Venue",
                List.of(new DelegatingEventPurchasePolicy()),
                List.of(new DelegatingEventDiscountPolicy())
        );
        event.addArea(EventTestFixtures.seatingArea(1, "10.00"));
        event.publish();

        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void mixed_areas_with_some_capacity_remaining_is_available() {
        SeatingEventArea seating = EventTestFixtures.seatingArea(2, "50.00");
        StandingEventArea standing = EventTestFixtures.standingArea(10, "20.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{standing}, new SeatingEventArea[]{seating});
        List<UUID> seatIds = seating.seats().keySet().stream().toList();
        event.holdSeats(seating.areaId(), seatIds, UUID.randomUUID());

        // seating is fully held but standing still has capacity
        assertThat(event.bookingStatus()).isEqualTo(EventAvailability.AVAILABLE);
    }
}
