package com.software_project_team_15b.Ticketmaster.Application.Event.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventServiceFeaturesIT {

    @Autowired
    EventManagementService service;

    @Autowired
    IEventRepository events;

    // ── Task 1: releaseSeats ─────────────────────────────────────────────────

    @Test
    void releaseSeats_frees_only_specified_seats() {
        SeatingSetup setup = createSeatingEvent(3, "50.00");
        UUID token = UUID.randomUUID();
        HoldReceipt hold = service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(hold.quantity()).isEqualTo(3);

        boolean released = service.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(released).isTrue();
        EventView view = service.getEvent(setup.eventId());
        long available = seatCount(view, setup.areaId(), "AVAILABLE");
        long held = seatCount(view, setup.areaId(), "HELD");
        assertThat(available).isEqualTo(1);
        assertThat(held).isEqualTo(2);
    }

    @Test
    void releaseSeats_returns_false_when_token_does_not_match_held_seat() {
        SeatingSetup setup = createSeatingEvent(2, "10.00");
        UUID realToken = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, realToken));

        boolean released = service.releaseSeats(
                setup.eventId(), UUID.randomUUID(), List.of(setup.seatIds().get(0)));

        assertThat(released).isFalse();
        EventView view = service.getEvent(setup.eventId());
        assertThat(seatCount(view, setup.areaId(), "HELD")).isEqualTo(2);
    }

    @Test
    void releaseSeats_works_for_standing_area_seats() {
        StandingSetup setup = createStandingEvent(4, "10.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), null, 4, token));
        List<UUID> heldSeatIds = standingHeldSeatIdsFor(setup.eventId(), setup.areaId(), token);
        assertThat(heldSeatIds).hasSize(4);

        boolean released = service.releaseSeats(
                setup.eventId(), token, List.of(heldSeatIds.get(0), heldSeatIds.get(1)));

        assertThat(released).isTrue();
        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
        Map<Boolean, Set<UUID>> avail = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), new java.util.HashSet<>(heldSeatIds));
        assertThat(avail.get(Boolean.TRUE))
                .containsExactlyInAnyOrder(heldSeatIds.get(0), heldSeatIds.get(1));
        assertThat(avail.get(Boolean.FALSE))
                .containsExactlyInAnyOrder(heldSeatIds.get(2), heldSeatIds.get(3));
    }

    @Test
    void releaseSeats_released_seat_can_be_reheld() {
        SeatingSetup setup = createSeatingEvent(2, "25.00");
        UUID tokenA = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, tokenA));

        service.releaseSeats(setup.eventId(), tokenA, List.of(setup.seatIds().get(0)));

        UUID tokenB = UUID.randomUUID();
        HoldReceipt second = service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(setup.seatIds().get(0)), null, tokenB));
        assertThat(second.quantity()).isEqualTo(1);
    }

    @Test
    void releaseSeats_empty_list_is_a_no_op() {
        SeatingSetup setup = createSeatingEvent(2, "25.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        service.releaseSeats(setup.eventId(), token, List.of());

        EventView view = service.getEvent(setup.eventId());
        assertThat(seatCount(view, setup.areaId(), "HELD")).isEqualTo(2);
    }

    // ── Task 2: getPrice ────────────────────────────────────────────────────

    @Test
    void getPrice_returns_correct_subtotal_without_discount() {
        SeatingSetup setup = createSeatingEvent(5, "30.00");
        PriceQuery query = new PriceQuery(setup.areaId(), 3, UUID.randomUUID(), null, null);

        PriceBreakdown breakdown = service.getPrice(setup.eventId(), query);

        assertThat(breakdown.basePrice()).isEqualTo(Money.of("30.00", "USD"));
        assertThat(breakdown.subtotal()).isEqualTo(Money.of("90.00", "USD"));
        assertThat(breakdown.discount()).isEqualTo(Money.of("0.00", "USD"));
        assertThat(breakdown.total()).isEqualTo(Money.of("90.00", "USD"));
    }

    @Test
    void getPrice_quantity_one_returns_base_price_as_total() {
        SeatingSetup setup = createSeatingEvent(1, "45.00");
        PriceQuery query = new PriceQuery(setup.areaId(), 1, UUID.randomUUID(), null, null);

        PriceBreakdown breakdown = service.getPrice(setup.eventId(), query);

        assertThat(breakdown.total()).isEqualTo(Money.of("45.00", "USD"));
    }

    // ── Task 3: getEventAvailability ─────────────────────────────────────────

    @Test
    void getEventAvailability_published_event_with_seats_is_available() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");

        assertThat(service.getEventAvailability(setup.eventId())).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void getEventAvailability_fully_held_event_is_sold_out() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        assertThat(service.getEventAvailability(setup.eventId())).isEqualTo(EventAvailability.SOLD_OUT);
    }

    @Test
    void getEventAvailability_cancelled_event_is_inactive() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        service.cancel(setup.eventId(), setup.callerId());

        assertThat(service.getEventAvailability(setup.eventId())).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void getEventAvailability_partial_release_restores_availability() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(service.getEventAvailability(setup.eventId())).isEqualTo(EventAvailability.SOLD_OUT);

        service.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(service.getEventAvailability(setup.eventId())).isEqualTo(EventAvailability.AVAILABLE);
    }

    // ── Task 4: getAreaAvailability ──────────────────────────────────────────

    @Test
    void getAreaAvailability_returns_true_when_area_has_available_seats() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
    }

    @Test
    void getAreaAvailability_returns_false_when_all_seats_held() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isFalse();
    }

    @Test
    void getAreaAvailability_returns_true_after_partial_release() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isFalse();

        service.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
    }

    @Test
    void getAreaAvailability_throws_when_area_not_found() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getAreaAvailability(setup.eventId(), unknownArea))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void getAreaAvailability_throws_when_event_not_found() {
        UUID unknownEvent = UUID.randomUUID();
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getAreaAvailability(unknownEvent, unknownArea))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── Task 5: getSeatsAvailability ─────────────────────────────────────────

    @Test
    void getSeatsAvailability_all_available_returns_all_in_true_bucket() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        Set<UUID> seatIds = new java.util.HashSet<>(setup.seatIds());

        Map<Boolean, Set<UUID>> result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), seatIds);

        assertThat(result.get(Boolean.TRUE)).containsExactlyInAnyOrderElementsOf(seatIds);
        assertThat(result.get(Boolean.FALSE)).isEmpty();
    }

    @Test
    void getSeatsAvailability_all_held_returns_all_in_false_bucket() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        Set<UUID> seatIds = new java.util.HashSet<>(setup.seatIds());

        Map<Boolean, Set<UUID>> result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), seatIds);

        assertThat(result.get(Boolean.TRUE)).isEmpty();
        assertThat(result.get(Boolean.FALSE)).containsExactlyInAnyOrderElementsOf(seatIds);
    }

    @Test
    void getSeatsAvailability_partitions_mixed_held_and_available_seats() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        UUID heldSeat = setup.seatIds().get(0);
        UUID freeSeat1 = setup.seatIds().get(1);
        UUID freeSeat2 = setup.seatIds().get(2);
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(heldSeat), null, UUID.randomUUID()));

        Map<Boolean, Set<UUID>> result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of(heldSeat, freeSeat1, freeSeat2));

        assertThat(result.get(Boolean.TRUE)).containsExactlyInAnyOrder(freeSeat1, freeSeat2);
        assertThat(result.get(Boolean.FALSE)).containsExactly(heldSeat);
    }

    @Test
    void getSeatsAvailability_unknown_seat_id_lands_in_false_bucket() {
        SeatingSetup setup = createSeatingEvent(1, "20.00");
        UUID realSeat = setup.seatIds().get(0);
        UUID ghost = UUID.randomUUID();

        Map<Boolean, Set<UUID>> result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of(realSeat, ghost));

        assertThat(result.get(Boolean.TRUE)).containsExactly(realSeat);
        assertThat(result.get(Boolean.FALSE)).containsExactly(ghost);
    }

    @Test
    void getSeatsAvailability_empty_input_returns_two_empty_buckets() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");

        Map<Boolean, Set<UUID>> result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of());

        assertThat(result).containsOnlyKeys(Boolean.TRUE, Boolean.FALSE);
        assertThat(result.get(Boolean.TRUE)).isEmpty();
        assertThat(result.get(Boolean.FALSE)).isEmpty();
    }

    @Test
    void getSeatsAvailability_throws_when_area_not_found() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getSeatsAvailability(
                setup.eventId(), unknownArea, Set.of(UUID.randomUUID())))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    // ── Task 6: areaSeats ────────────────────────────────────────────────────

    @Test
    void areaSeats_returns_all_seats_of_a_seating_area_with_status() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        UUID heldSeat = setup.seatIds().get(0);
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(heldSeat), null, UUID.randomUUID()));

        List<EventView.SeatView> seats = service.areaSeats(setup.eventId(), setup.areaId());

        assertThat(seats).hasSize(3);
        assertThat(seats).extracting(EventView.SeatView::seatId)
                .containsExactlyInAnyOrderElementsOf(setup.seatIds());
        assertThat(seats).filteredOn(s -> s.seatId().equals(heldSeat))
                .extracting(EventView.SeatView::status)
                .containsExactly("HELD");
        assertThat(seats).filteredOn(s -> !s.seatId().equals(heldSeat))
                .extracting(EventView.SeatView::status)
                .containsOnly("AVAILABLE");
    }

    @Test
    void areaSeats_returns_synthetic_seats_for_a_standing_area() {
        StandingSetup setup = createStandingEvent(4, "10.00");

        List<EventView.SeatView> seats = service.areaSeats(setup.eventId(), setup.areaId());

        assertThat(seats).hasSize(4);
        assertThat(seats).extracting(EventView.SeatView::status).containsOnly("AVAILABLE");
        assertThat(seats).extracting(EventView.SeatView::row).containsOnly("GA");
    }

    @Test
    void areaSeats_throws_when_area_not_found() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        assertThatThrownBy(() -> service.areaSeats(setup.eventId(), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void areaSeats_throws_when_event_not_found() {
        assertThatThrownBy(() -> service.areaSeats(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds, UUID callerId) {}
    private record StandingSetup(UUID eventId, UUID areaId, UUID callerId) {}

    private StandingSetup createStandingEvent(int capacity, String price) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of(price, "USD"), AddAreaCommand.AreaType.STANDING, capacity, null), caller);
        service.publish(eventId, caller);
        return new StandingSetup(eventId, areaId, caller);
    }

    private List<UUID> standingHeldSeatIdsFor(UUID eventId, UUID areaId, UUID token) {
        StandingEventArea area = (StandingEventArea) events.findById(eventId).orElseThrow()
                .areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow();
        return area.seats().values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .map(s -> s.seatId())
                .toList();
    }

    private SeatingSetup createSeatingEvent(int seatCount, String price) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        List<AddAreaCommand.SeatSpec> specs = new java.util.ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        }
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), caller);
        service.publish(eventId, caller);

        List<UUID> seatIds = service.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .map(EventView.SeatView::seatId)
                .toList();
        return new SeatingSetup(eventId, areaId, seatIds, caller);
    }

    private long seatCount(EventView view, UUID areaId, String status) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .filter(s -> s.status().equals(status))
                .count();
    }
}
