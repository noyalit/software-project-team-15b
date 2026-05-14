package com.software_project_team_15b.Ticketmaster.Application.Event.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventCatalogMutabilityIT {

    @Autowired
    EventManagementService service;

    // ── updateEvent ──────────────────────────────────────────────────────────

    @Test
    void updateEvent_patches_descriptive_fields() {
        Setup s = createDraftSeatingEvent(2, "10.00");
        Instant newStart = Instant.now().plusSeconds(7200);

        service.updateEvent(s.eventId(),
                new UpdateEventCommand("Renamed", "New Artist", Category.SPORTS, newStart, "New Hall"),
                s.callerId());

        EventDTO view = service.getEvent(s.eventId());
        assertThat(view.name()).isEqualTo("Renamed");
        assertThat(view.artist()).isEqualTo("New Artist");
        assertThat(view.category()).isEqualTo(Category.SPORTS);
        assertThat(view.location()).isEqualTo("New Hall");
        assertThat(view.startsAt()).isEqualTo(newStart);
    }

    @Test
    void updateEvent_with_only_some_fields_leaves_others_unchanged() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        EventDTO before = service.getEvent(s.eventId());

        service.updateEvent(s.eventId(),
                new UpdateEventCommand(null, null, null, null, "Different Hall"), s.callerId());

        EventDTO after = service.getEvent(s.eventId());
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.artist()).isEqualTo(before.artist());
        assertThat(after.location()).isEqualTo("Different Hall");
    }

    @Test
    void updateEvent_works_after_publish() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.updateEvent(s.eventId(),
                new UpdateEventCommand("Live Update", null, null, null, null), s.callerId());

        assertThat(service.getEvent(s.eventId()).name()).isEqualTo("Live Update");
    }

    @Test
    void updateEvent_on_cancelled_event_throws() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.cancel(s.eventId(), s.callerId());

        assertThatThrownBy(() -> service.updateEvent(s.eventId(),
                new UpdateEventCommand("X", null, null, null, null), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void updateEvent_unknown_event_throws() {
        assertThatThrownBy(() -> service.updateEvent(UUID.randomUUID(),
                new UpdateEventCommand("X", null, null, null, null), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── updateArea ───────────────────────────────────────────────────────────

    @Test
    void updateArea_renames_seating_area() {
        Setup s = createPublishedSeatingEvent(2, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand("VIP", null, null), s.callerId());

        assertThat(areaOf(service.getEvent(s.eventId()), s.areaId()).name()).isEqualTo("VIP");
    }

    @Test
    void updateArea_reprice_is_reflected_in_getPrice() {
        Setup s = createPublishedSeatingEvent(3, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, Money.of("25.00", "USD"), null), s.callerId());

        PriceBreakdown q = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(q.basePrice()).isEqualTo(Money.of("25.00", "USD"));
        assertThat(q.total()).isEqualTo(Money.of("50.00", "USD"));
    }

    @Test
    void updateArea_resizes_standing_capacity_up() {
        Setup s = createPublishedStandingEvent(5, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 10), s.callerId());

        assertThat(areaOf(service.getEvent(s.eventId()), s.areaId()).availableCapacity()).isEqualTo(10);
    }

    @Test
    void updateArea_resizes_standing_capacity_down_preserves_held_quantity() {
        Setup s = createPublishedStandingEvent(10, "10.00");
        UUID token = UUID.randomUUID();
        service.hold(s.eventId(), new HoldCommand(s.areaId(), null, 3, token));

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 5), s.callerId());

        EventDTO view = service.getEvent(s.eventId());
        assertThat(areaOf(view, s.areaId()).availableCapacity()).isEqualTo(2);
    }

    @Test
    void updateArea_shrink_below_held_floor_throws() {
        Setup s = createPublishedStandingEvent(10, "10.00");
        service.hold(s.eventId(), new HoldCommand(s.areaId(), null, 5, UUID.randomUUID()));

        assertThatThrownBy(() -> service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 3), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void updateArea_standing_capacity_on_seating_area_throws() {
        Setup s = createPublishedSeatingEvent(2, "10.00");

        assertThatThrownBy(() -> service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 50), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("standingCapacity");
    }

    @Test
    void updateArea_unknown_area_throws() {
        Setup s = createPublishedSeatingEvent(1, "10.00");

        assertThatThrownBy(() -> service.updateArea(s.eventId(), UUID.randomUUID(),
                new UpdateAreaCommand("X", null, null), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    // ── removeArea ───────────────────────────────────────────────────────────

    @Test
    void removeArea_drops_a_draft_area() {
        Setup s = createDraftSeatingEvent(1, "10.00");

        service.removeArea(s.eventId(), s.areaId(), s.callerId());

        assertThat(service.getEvent(s.eventId()).areas()).isEmpty();
    }

    @Test
    void removeArea_after_publish_throws() {
        Setup s = createPublishedSeatingEvent(1, "10.00");

        assertThatThrownBy(() -> service.removeArea(s.eventId(), s.areaId(), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class);

        assertThat(service.getEvent(s.eventId()).areas()).hasSize(1);
    }

    // ── replacePurchasePolicies ──────────────────────────────────────────────

    @Test
    void replacePurchasePolicies_changes_validation_outcome() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        service.replacePurchasePolicies(s.eventId(),
                List.of(new MaxTicketsPerOrderPolicy(2)), s.callerId());

        PurchaseRequest req = new PurchaseRequest(s.eventId(), s.areaId(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 5, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(s.eventId(), req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void replacePurchasePolicies_loosening_lets_request_pass() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        UUID company = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID strict = service.createEvent(new CreateEventCommand(
                company, "Strict", "A", Category.OTHER,
                Instant.now().plusSeconds(86400), "V",
                List.of(new MaxTicketsPerOrderPolicy(1), new AgeRestrictionPolicy(18)), null), caller);

        service.replacePurchasePolicies(strict, List.of(), caller);

        PurchaseRequest req = new PurchaseRequest(strict, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 99, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(strict, req))
                .doesNotThrowAnyException();
    }

    @Test
    void replacePurchasePolicies_null_throws() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        assertThatThrownBy(() -> service.replacePurchasePolicies(s.eventId(), null, s.callerId()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void replacePurchasePolicies_on_cancelled_event_throws() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.cancel(s.eventId(), s.callerId());

        List<IEventPurchasePolicy> p = List.of();
        assertThatThrownBy(() -> service.replacePurchasePolicies(s.eventId(), p, s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    // ── replaceDiscountPolicies ──────────────────────────────────────────────

    @Test
    void replaceDiscountPolicies_changes_total_in_getPrice() {
        Setup s = createPublishedSeatingEvent(3, "100.00");
        PriceBreakdown before = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(before.total()).isEqualTo(Money.of("200.00", "USD"));

        IEventDiscountPolicy half = new EarlyBirdDiscountPolicy(
                java.math.BigDecimal.valueOf(50),
                Instant.now().plusSeconds(86400));
        service.replaceDiscountPolicies(s.eventId(), List.of(half), s.callerId());

        PriceBreakdown after = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(after.total()).isEqualTo(Money.of("100.00", "USD"));
        assertThat(after.discount()).isEqualTo(Money.of("100.00", "USD"));
    }

    @Test
    void replaceDiscountPolicies_empty_clears_discounts() {
        Setup s = createPublishedSeatingEventWithEarlyBird(2, "50.00", 50);
        PriceBreakdown discounted = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(discounted.discount()).isEqualTo(Money.of("50.00", "USD"));

        service.replaceDiscountPolicies(s.eventId(), List.of(), s.callerId());

        PriceBreakdown plain = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(plain.discount()).isEqualTo(Money.of("0.00", "USD"));
    }

    @Test
    void replaceDiscountPolicies_null_element_throws() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        List<IEventDiscountPolicy> bad = new ArrayList<>();
        bad.add(null);
        assertThatThrownBy(() -> service.replaceDiscountPolicies(s.eventId(), bad, s.callerId()))
                .isInstanceOf(NullPointerException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Setup(UUID eventId, UUID areaId, UUID callerId) {}

    private Setup createDraftSeatingEvent(int seatCount, String price) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), caller);
        return new Setup(eventId, areaId, caller);
    }

    private Setup createPublishedSeatingEvent(int seatCount, String price) {
        Setup s = createDraftSeatingEvent(seatCount, price);
        service.publish(s.eventId(), s.callerId());
        return s;
    }

    private Setup createPublishedStandingEvent(int capacity, String price) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of(price, "USD"), AddAreaCommand.AreaType.STANDING, capacity, null), caller);
        service.publish(eventId, caller);
        return new Setup(eventId, areaId, caller);
    }

    private Setup createPublishedSeatingEventWithEarlyBird(int seatCount, String price, int percentOff) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        IEventDiscountPolicy earlyBird = new EarlyBirdDiscountPolicy(
                java.math.BigDecimal.valueOf(percentOff),
                Instant.now().plusSeconds(86400));
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "EB", "A", Category.CONCERT,
                Instant.now().plusSeconds(86400), "V", null, List.of(earlyBird)), caller);
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), caller);
        service.publish(eventId, caller);
        return new Setup(eventId, areaId, caller);
    }

    private static EventDTO.AreaView areaOf(EventDTO view, UUID areaId) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow();
    }
}
