package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventPartialReleaseTest {

    @Test
    void partial_release_frees_only_specified_seats() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), all, token);

        boolean released = event.releaseSeats(token, List.of(all.get(0)));

        assertThat(released).isTrue();
        assertThat(area.heldCount()).isEqualTo(2);
        assertThat(area.availableCapacity()).isEqualTo(1);
    }

    @Test
    void partial_release_leaves_remaining_seats_held() {
        SeatingEventArea area = EventTestFixtures.seatingArea(4, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), all, token);

        event.releaseSeats(token, List.of(all.get(0), all.get(1)));

        assertThat(area.heldCount()).isEqualTo(2);
        assertThat(area.availableCapacity()).isEqualTo(2);
        assertThat(area.seatIdsHeldBy(token)).containsExactlyInAnyOrder(all.get(2), all.get(3));
    }

    @Test
    void partial_release_ignores_seats_held_by_different_token() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(all.get(0)), tokenA);
        event.holdSeats(area.areaId(), List.of(all.get(1)), tokenB);

        boolean released = event.releaseSeats(tokenA, List.of(all.get(1)));

        assertThat(released).isFalse();
        assertThat(area.heldCount()).isEqualTo(2);
    }

    @Test
    void partial_release_with_unknown_seat_id_returns_false() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), all, token);

        boolean released = event.releaseSeats(token, List.of(UUID.randomUUID()));

        assertThat(released).isFalse();
        assertThat(area.heldCount()).isEqualTo(2);
    }

    @Test
    void partial_release_with_empty_list_returns_false() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), all, token);

        boolean released = event.releaseSeats(token, List.of());

        assertThat(released).isFalse();
        assertThat(area.heldCount()).isEqualTo(2);
    }

    @Test
    void released_seat_can_be_reheld_by_another_token() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "50.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID tokenA = UUID.randomUUID();
        event.holdSeats(area.areaId(), all, tokenA);

        event.releaseSeats(tokenA, List.of(all.get(0)));

        UUID tokenB = UUID.randomUUID();
        HoldReceipt receipt = event.holdSeats(area.areaId(), List.of(all.get(0)), tokenB);
        assertThat(receipt.seatIds()).containsExactly(all.get(0));
        assertThat(area.heldCount()).isEqualTo(2);
    }

    @Test
    void seating_area_release_specific_seats_directly() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "10.00");
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        area.holdSeats(all, token);

        boolean released = area.releaseSpecificSeats(List.of(all.get(0), all.get(2)), token);

        assertThat(released).isTrue();
        assertThat(area.heldCount()).isEqualTo(1);
        assertThat(area.seatIdsHeldBy(token)).containsExactly(all.get(1));
    }

    @Test
    void seating_area_release_specific_seats_wrong_token_no_op() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        List<UUID> all = area.seats().keySet().stream().toList();
        UUID tokenA = UUID.randomUUID();
        area.holdSeats(all, tokenA);

        boolean released = area.releaseSpecificSeats(all, UUID.randomUUID());

        assertThat(released).isFalse();
        assertThat(area.heldCount()).isEqualTo(2);
    }
}
