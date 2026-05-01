package com.software_project_team_15b.Ticketmaster.Application.Event.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventServiceFeaturesIT {

    @Autowired
    EventManagementService service;

    // ── Task 1: releaseSeats ─────────────────────────────────────────────────

    @Test
    void releaseSeats_frees_only_specified_seats() {
        SeatingSetup setup = createSeatingEvent(3, "50.00");
        UUID token = UUID.randomUUID();
        HoldReceipt hold = service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(hold.quantity()).isEqualTo(3);

        service.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        EventView view = service.getEvent(setup.eventId());
        long available = seatCount(view, setup.areaId(), "AVAILABLE");
        long held = seatCount(view, setup.areaId(), "HELD");
        assertThat(available).isEqualTo(1);
        assertThat(held).isEqualTo(2);
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds, UUID callerId) {}

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
