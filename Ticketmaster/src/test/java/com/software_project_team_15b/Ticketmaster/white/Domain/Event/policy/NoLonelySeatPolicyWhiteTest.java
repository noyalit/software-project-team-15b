package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.NoLonelySeatPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoLonelySeatPolicyWhiteTest {

    private static Event eventWith(SeatingEventArea area) {
        Event event = new Event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Show",
                "Artist",
                Category.CONCERT,
                Instant.now().plusSeconds(3600),
                "Venue",
                List.of(),
                List.of());
        event.addArea(area);
        event.publish();
        return event;
    }

    private static SeatingEventArea row(String rowLabel, int seats) {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), rowLabel, Money.of("10.00", "USD"));
        for (int i = 1; i <= seats; i++) {
            area.addSeat(new Seat(UUID.randomUUID(), rowLabel, String.valueOf(i)));
        }
        return area;
    }

    private static PurchaseRequest request(UUID areaId, List<UUID> seatIds) {
        return new PurchaseRequest(
                UUID.randomUUID(), areaId, UUID.randomUUID(),
                null, Math.max(1, seatIds.size()), seatIds, null);
    }

    @Test
    void GivenNullSeatIds_WhenValidate_ThenDoesNotThrow() {
        SeatingEventArea area = row("A", 4);
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();
        PurchaseRequest req = new PurchaseRequest(
                UUID.randomUUID(), area.areaId(), UUID.randomUUID(), null, 1, null, null);

        assertThatCode(() -> policy.validate(req, event)).doesNotThrowAnyException();
    }

    @Test
    void GivenEmptySeatIds_WhenValidate_ThenDoesNotThrow() {
        SeatingEventArea area = row("A", 4);
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();

        assertThatCode(() -> policy.validate(request(area.areaId(), List.of()), event))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenAreaIdNotFound_WhenValidate_ThenDoesNotThrow() {
        SeatingEventArea area = row("A", 4);
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();
        UUID someSeat = area.seats().keySet().iterator().next();

        assertThatCode(() -> policy.validate(request(UUID.randomUUID(), List.of(someSeat)), event))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenStandingAreaTargeted_WhenValidate_ThenDoesNotThrow() {
        StandingEventArea standing = new StandingEventArea(UUID.randomUUID(), "Floor", Money.of("10.00", "USD"), 5);
        Event event = new Event(
                UUID.randomUUID(), UUID.randomUUID(), "Show", "Artist",
                Category.CONCERT, Instant.now().plusSeconds(3600), "Venue",
                List.of(), List.of());
        event.addArea(standing);
        event.publish();
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();
        UUID firstStanding = standing.seats().keySet().iterator().next();

        assertThatCode(() -> policy.validate(request(standing.areaId(), List.of(firstStanding)), event))
                .doesNotThrowAnyException();
    }

    private static UUID seatNumbered(SeatingEventArea area, String number) {
        return area.seats().values().stream()
                .filter(s -> s.number().equals(number))
                .findFirst().orElseThrow().seatId();
    }

    @Test
    void GivenHoldLeavesOrphanBetweenSold_WhenValidate_ThenThrowsPolicyViolation() {
        SeatingEventArea area = row("A", 3);
        // mark seat 1 and seat 3 as held already (simulating sold neighbours)
        area.seats().get(seatNumbered(area, "1")).markHeld(UUID.randomUUID());
        area.seats().get(seatNumbered(area, "3")).markHeld(UUID.randomUUID());
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();

        // seat 2 is lonely between two held seats — propose seat 1 (already held; the validator
        // walks every available seat regardless of what was proposed)
        UUID seat1 = seatNumbered(area, "1");
        assertThatThrownBy(() -> policy.validate(request(area.areaId(), List.of(seat1)), event))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("lonely");
    }

    @Test
    void GivenHoldAtRowEnd_WhenValidate_ThenDoesNotThrow() {
        SeatingEventArea area = row("A", 4);
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();

        // hold seat 1 — seat 2 has only one neighbour blocked (the proposed seat); seat 3,4 free
        UUID seat1 = seatNumbered(area, "1");
        assertThatCode(() -> policy.validate(request(area.areaId(), List.of(seat1)), event))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonNumericSeatNumbers_WhenValidate_ThenStillEvaluatesWithoutCrashing() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "A", Money.of("10.00", "USD"));
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        area.addSeat(new Seat(s1, "A", "alpha"));
        area.addSeat(new Seat(s2, "A", "beta"));
        area.addSeat(new Seat(s3, "A", "gamma"));
        Event event = eventWith(area);
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();

        assertThatCode(() -> policy.validate(request(area.areaId(), List.of(s1)), event))
                .doesNotThrowAnyException();
    }
}
