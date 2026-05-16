package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventLifecycleTest {

    // ─── Status transitions ────────────────────────────────────────────────────

    @Test
    void new_event_is_in_draft_status() {
        Event event = EventTestFixtures.draft();
        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void publish_transitions_draft_to_published() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.draft();
        event.addArea(area);
        event.publish();
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publish_from_published_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(event::publish)
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void cancel_transitions_to_cancelled() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_is_idempotent() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_draft_event_transitions_to_cancelled() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    // ─── Area management ──────────────────────────────────────────────────────

    @Test
    void addArea_after_publish_throws() {
        SeatingEventArea area1 = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area1);
        SeatingEventArea area2 = EventTestFixtures.seatingArea(2, "20.00");
        assertThatThrownBy(() -> event.addArea(area2))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void addArea_duplicate_areaId_throws() {
        Event event = EventTestFixtures.draft();
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        event.addArea(area);
        assertThatThrownBy(() -> event.addArea(area))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ─── Pricing ──────────────────────────────────────────────────────────────

    @Test
    void priceFor_returns_base_price_times_quantity() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "30.00");
        Event event = EventTestFixtures.published(area);
        Money price = event.priceFor(area.areaId(), 3);
        assertThat(price).isEqualTo(Money.of("90.00", "USD"));
    }

    @Test
    void priceFor_unknown_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.priceFor(UUID.randomUUID(), 1))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ─── heldCountIn ──────────────────────────────────────────────────────────

    @Test
    void heldCountIn_seating_area_returns_held_seat_count() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().limit(2).toList();
        event.holdSeats(area.areaId(), ids, UUID.randomUUID());
        assertThat(event.heldCountIn(area.areaId())).isEqualTo(2);
    }

    @Test
    void heldCountIn_standing_area_returns_held_quantity() {
        StandingEventArea area = EventTestFixtures.standingArea(10, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        event.holdStanding(area.areaId(), 4, UUID.randomUUID());
        assertThat(event.heldCountIn(area.areaId())).isEqualTo(4);
    }

    @Test
    void heldCountIn_unknown_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.heldCountIn(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    @Test
    void holdSeats_with_duplicate_seatIds_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(seatId, seatId), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void holdSeats_on_standing_area_throws() {
        StandingEventArea area = EventTestFixtures.standingArea(5, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void holdStanding_on_seating_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.holdStanding(area.areaId(), 1, UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void confirmHold_on_cancelled_event_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.cancel();
        assertThatThrownBy(() -> event.confirmHold(token))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void releaseHold_unknown_token_returns_false() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThat(event.releaseHold(UUID.randomUUID())).isFalse();
    }

    // ─── SOLD_OUT transition ──────────────────────────────────────────────────

    @Test
    void event_transitions_to_sold_out_when_last_seat_confirmed() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void sold_out_event_rejects_new_seat_holds() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.confirmHold(token);

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        SeatingEventArea area2 = EventTestFixtures.seatingArea(1, "10.00");
        assertThatThrownBy(() -> event.holdSeats(area2.areaId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void event_does_not_transition_to_sold_out_while_capacity_remains() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(ids.get(0)), token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void standing_event_transitions_to_sold_out_when_all_capacity_confirmed() {
        StandingEventArea area = EventTestFixtures.standingArea(3, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        UUID token = UUID.randomUUID();
        event.holdStanding(area.areaId(), 3, token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
    }
}
