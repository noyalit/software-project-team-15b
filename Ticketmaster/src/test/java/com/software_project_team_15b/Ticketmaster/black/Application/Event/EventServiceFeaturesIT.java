package com.software_project_team_15b.Ticketmaster.black.Application.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport.FounderActor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventServiceFeaturesIT {

    @Autowired
    EventManagementService service;

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IEventRepository events;

    @Autowired
    IMemberRepository memberRepository;

    @Autowired
    ICompanyRepository companyRepository;

    // ── Task 1: releaseSeats ─────────────────────────────────────────────────

    @Test
    void GivenThreeHeldSeats_WhenReleaseOne_ThenOnlyThatSeatIsAvailable() {
        SeatingSetup setup = createSeatingEvent(3, "50.00");
        UUID token = UUID.randomUUID();
        HoldReceipt hold = eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(hold.quantity()).isEqualTo(3);

        boolean released = eventDomainService.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(released).isTrue();
        EventDTO view = service.getEvent(setup.eventId());
        long available = seatCount(view, setup.areaId(), "AVAILABLE");
        long held = seatCount(view, setup.areaId(), "HELD");
        assertThat(available).isEqualTo(1);
        assertThat(held).isEqualTo(2);
    }

    @Test
    void GivenHeldSeats_WhenReleaseWithWrongToken_ThenReturnsFalseAndSeatsStayHeld() {
        SeatingSetup setup = createSeatingEvent(2, "10.00");
        UUID realToken = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, realToken));

        boolean released = eventDomainService.releaseSeats(
                setup.eventId(), UUID.randomUUID(), List.of(setup.seatIds().get(0)));

        assertThat(released).isFalse();
        EventDTO view = service.getEvent(setup.eventId());
        assertThat(seatCount(view, setup.areaId(), "HELD")).isEqualTo(2);
    }

    @Test
    void GivenHeldStandingSeats_WhenReleaseSubset_ThenReleasedSeatsAreAvailable() {
        StandingSetup setup = createStandingEvent(4, "10.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), null, 4, token));
        List<UUID> heldSeatIds = standingHeldSeatIdsFor(setup.eventId(), setup.areaId(), token);
        assertThat(heldSeatIds).hasSize(4);

        boolean released = eventDomainService.releaseSeats(
                setup.eventId(), token, List.of(heldSeatIds.get(0), heldSeatIds.get(1)));

        assertThat(released).isTrue();
        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
        SeatsAvailabilityDTO avail = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), new java.util.HashSet<>(heldSeatIds));
        assertThat(avail.available())
                .containsExactlyInAnyOrder(heldSeatIds.get(0), heldSeatIds.get(1));
        assertThat(avail.unavailable())
                .containsExactlyInAnyOrder(heldSeatIds.get(2), heldSeatIds.get(3));
    }

    @Test
    void GivenSeatReleased_WhenHoldByDifferentToken_ThenHoldSucceeds() {
        SeatingSetup setup = createSeatingEvent(2, "25.00");
        UUID tokenA = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, tokenA));

        eventDomainService.releaseSeats(setup.eventId(), tokenA, List.of(setup.seatIds().get(0)));

        UUID tokenB = UUID.randomUUID();
        HoldReceipt second = eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(setup.seatIds().get(0)), null, tokenB));
        assertThat(second.quantity()).isEqualTo(1);
    }

    @Test
    void GivenHeldSeats_WhenReleaseEmptyList_ThenNoSeatsChange() {
        SeatingSetup setup = createSeatingEvent(2, "25.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        eventDomainService.releaseSeats(setup.eventId(), token, List.of());

        EventDTO view = service.getEvent(setup.eventId());
        assertThat(seatCount(view, setup.areaId(), "HELD")).isEqualTo(2);
    }

    // ── Task 2: getPrice ────────────────────────────────────────────────────

    @Test
    void GivenAreaWithBasePrice_WhenGetPriceForQuantity_ThenSubtotalEqualsBaseTimesQuantity() {
        SeatingSetup setup = createSeatingEvent(5, "30.00");
        PriceQuery query = new PriceQuery(setup.areaId(), 3, UUID.randomUUID(), null, null);

        PriceBreakdownDTO breakdown = service.getPrice(setup.eventId(), query);

        assertThat(breakdown.basePrice()).isEqualTo(MoneyDTO.from(Money.of("30.00", "USD")));
        assertThat(breakdown.subtotal()).isEqualTo(MoneyDTO.from(Money.of("90.00", "USD")));
        assertThat(breakdown.discount()).isEqualTo(MoneyDTO.from(Money.of("0.00", "USD")));
        assertThat(breakdown.total()).isEqualTo(MoneyDTO.from(Money.of("90.00", "USD")));
    }

    @Test
    void GivenQuantityOne_WhenGetPrice_ThenTotalEqualsBasePrice() {
        SeatingSetup setup = createSeatingEvent(1, "45.00");
        PriceQuery query = new PriceQuery(setup.areaId(), 1, UUID.randomUUID(), null, null);

        PriceBreakdownDTO breakdown = service.getPrice(setup.eventId(), query);

        assertThat(breakdown.total()).isEqualTo(MoneyDTO.from(Money.of("45.00", "USD")));
    }

    // ── Task 3: getEventAvailability ─────────────────────────────────────────

    @Test
    void GivenPublishedEventWithSeats_WhenGetEventAvailability_ThenStatusIsAvailable() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");

        assertThat(service.getEventAvailability(setup.eventId()).status()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    void GivenAllSeatsHeld_WhenGetEventAvailability_ThenStatusIsSoldOut() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        assertThat(service.getEventAvailability(setup.eventId()).status()).isEqualTo(EventAvailability.SOLD_OUT);
    }

    @Test
    void GivenCancelledEvent_WhenGetEventAvailability_ThenStatusIsInactive() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        service.cancel(setup.eventId(), setup.callerId());

        assertThat(service.getEventAvailability(setup.eventId()).status()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    void GivenSoldOutEvent_WhenReleaseOneSeat_ThenAvailabilityReturnsToAvailable() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(service.getEventAvailability(setup.eventId()).status()).isEqualTo(EventAvailability.SOLD_OUT);

        eventDomainService.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(service.getEventAvailability(setup.eventId()).status()).isEqualTo(EventAvailability.AVAILABLE);
    }

    // ── Task 4: getAreaAvailability ──────────────────────────────────────────

    @Test
    void GivenAreaWithFreeSeats_WhenGetAreaAvailability_ThenReturnsTrue() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
    }

    @Test
    void GivenAreaWithAllSeatsHeld_WhenGetAreaAvailability_ThenReturnsFalse() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isFalse();
    }

    @Test
    void GivenFullyHeldArea_WhenReleaseOneSeat_ThenGetAreaAvailabilityReturnsTrue() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isFalse();

        eventDomainService.releaseSeats(setup.eventId(), token, List.of(setup.seatIds().get(0)));

        assertThat(service.getAreaAvailability(setup.eventId(), setup.areaId())).isTrue();
    }

    @Test
    void GivenUnknownAreaId_WhenGetAreaAvailability_ThenThrowsInvalidEventState() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getAreaAvailability(setup.eventId(), unknownArea))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void GivenUnknownEventId_WhenGetAreaAvailability_ThenThrowsInvalidEventState() {
        UUID unknownEvent = UUID.randomUUID();
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getAreaAvailability(unknownEvent, unknownArea))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── Task 5: getSeatsAvailability ─────────────────────────────────────────

    @Test
    void GivenAllSeatsAvailable_WhenGetSeatsAvailability_ThenAllInAvailableBucket() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        Set<UUID> seatIds = new java.util.HashSet<>(setup.seatIds());

        SeatsAvailabilityDTO result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), seatIds);

        assertThat(result.available()).containsExactlyInAnyOrderElementsOf(seatIds);
        assertThat(result.unavailable()).isEmpty();
    }

    @Test
    void GivenAllSeatsHeld_WhenGetSeatsAvailability_ThenAllInUnavailableBucket() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), setup.seatIds(), null, token));
        Set<UUID> seatIds = new java.util.HashSet<>(setup.seatIds());

        SeatsAvailabilityDTO result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), seatIds);

        assertThat(result.available()).isEmpty();
        assertThat(result.unavailable()).containsExactlyInAnyOrderElementsOf(seatIds);
    }

    @Test
    void GivenMixedHeldAndAvailableSeats_WhenGetSeatsAvailability_ThenSeatsAreCorrectlyPartitioned() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        UUID heldSeat = setup.seatIds().get(0);
        UUID freeSeat1 = setup.seatIds().get(1);
        UUID freeSeat2 = setup.seatIds().get(2);
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(heldSeat), null, UUID.randomUUID()));

        SeatsAvailabilityDTO result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of(heldSeat, freeSeat1, freeSeat2));

        assertThat(result.available()).containsExactlyInAnyOrder(freeSeat1, freeSeat2);
        assertThat(result.unavailable()).containsExactly(heldSeat);
    }

    @Test
    void GivenUnknownSeatId_WhenGetSeatsAvailability_ThenUnknownSeatIsInUnavailableBucket() {
        SeatingSetup setup = createSeatingEvent(1, "20.00");
        UUID realSeat = setup.seatIds().get(0);
        UUID ghost = UUID.randomUUID();

        SeatsAvailabilityDTO result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of(realSeat, ghost));

        assertThat(result.available()).containsExactly(realSeat);
        assertThat(result.unavailable()).containsExactly(ghost);
    }

    @Test
    void GivenEmptySeatIdSet_WhenGetSeatsAvailability_ThenBothBucketsAreEmpty() {
        SeatingSetup setup = createSeatingEvent(2, "20.00");

        SeatsAvailabilityDTO result = service.getSeatsAvailability(
                setup.eventId(), setup.areaId(), Set.of());

        assertThat(result.available()).isEmpty();
        assertThat(result.unavailable()).isEmpty();
    }

    @Test
    void GivenUnknownAreaId_WhenGetSeatsAvailability_ThenThrowsInvalidEventState() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> service.getSeatsAvailability(
                setup.eventId(), unknownArea, Set.of(UUID.randomUUID())))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    // ── Task 6: areaSeats ────────────────────────────────────────────────────

    @Test
    void GivenSeatingAreaWithOneHeldSeat_WhenAreaSeats_ThenReturnsAllSeatsWithCorrectStatuses() {
        SeatingSetup setup = createSeatingEvent(3, "20.00");
        UUID heldSeat = setup.seatIds().get(0);
        eventDomainService.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(heldSeat), null, UUID.randomUUID()));

        List<EventDTO.SeatView> seats = service.areaSeats(setup.eventId(), setup.areaId());

        assertThat(seats).hasSize(3);
        assertThat(seats).extracting(EventDTO.SeatView::seatId)
                .containsExactlyInAnyOrderElementsOf(setup.seatIds());
        assertThat(seats).filteredOn(s -> s.seatId().equals(heldSeat))
                .extracting(EventDTO.SeatView::status)
                .containsExactly("HELD");
        assertThat(seats).filteredOn(s -> !s.seatId().equals(heldSeat))
                .extracting(EventDTO.SeatView::status)
                .containsOnly("AVAILABLE");
    }

    @Test
    void GivenStandingArea_WhenAreaSeats_ThenReturnsSyntheticAvailableSeats() {
        StandingSetup setup = createStandingEvent(4, "10.00");

        List<EventDTO.SeatView> seats = service.areaSeats(setup.eventId(), setup.areaId());

        assertThat(seats).hasSize(4);
        assertThat(seats).extracting(EventDTO.SeatView::status).containsOnly("AVAILABLE");
        assertThat(seats).extracting(EventDTO.SeatView::row).containsOnly("GA");
    }

    @Test
    void GivenUnknownAreaId_WhenAreaSeats_ThenThrowsInvalidEventState() {
        SeatingSetup setup = createSeatingEvent(1, "10.00");
        assertThatThrownBy(() -> service.areaSeats(setup.eventId(), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void GivenUnknownEventId_WhenAreaSeats_ThenThrowsInvalidEventState() {
        assertThatThrownBy(() -> service.areaSeats(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds, UUID callerId) {}
    private record StandingSetup(UUID eventId, UUID areaId, UUID callerId) {}

    private StandingSetup createStandingEvent(int capacity, String price) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository, companyRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), actor.memberId());
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of(price, "USD"), AddAreaCommand.AreaType.STANDING, capacity, null), actor.memberId());
        service.publish(eventId, actor.memberId());
        return new StandingSetup(eventId, areaId, actor.memberId());
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
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository, companyRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), actor.memberId());
        List<AddAreaCommand.SeatSpec> specs = new java.util.ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        }
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), actor.memberId());
        service.publish(eventId, actor.memberId());

        List<UUID> seatIds = service.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .map(EventDTO.SeatView::seatId)
                .toList();
        return new SeatingSetup(eventId, areaId, seatIds, actor.memberId());
    }

    private long seatCount(EventDTO view, UUID areaId, String status) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .filter(s -> s.status().equals(status))
                .count();
    }
}
