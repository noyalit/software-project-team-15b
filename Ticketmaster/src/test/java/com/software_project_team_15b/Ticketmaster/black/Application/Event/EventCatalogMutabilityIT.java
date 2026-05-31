package com.software_project_team_15b.Ticketmaster.black.Application.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport.FounderActor;
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

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IMemberRepository memberRepository;

    // ── updateEvent ──────────────────────────────────────────────────────────

    @Test
    void GivenDraftEvent_WhenUpdateEventWithAllFields_ThenAllFieldsArePatched() {
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
    void GivenDraftEvent_WhenUpdateEventWithPartialFields_ThenOnlyProvidedFieldsChange() {
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
    void GivenPublishedEvent_WhenUpdateEvent_ThenChangesArePersisted() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.updateEvent(s.eventId(),
                new UpdateEventCommand("Live Update", null, null, null, null), s.callerId());

        assertThat(service.getEvent(s.eventId()).name()).isEqualTo("Live Update");
    }

    @Test
    void GivenCancelledEvent_WhenUpdateEvent_ThenThrowsInvalidEventState() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.cancel(s.eventId(), s.callerId());

        assertThatThrownBy(() -> service.updateEvent(s.eventId(),
                new UpdateEventCommand("X", null, null, null, null), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void GivenUnknownEventId_WhenUpdateEvent_ThenThrowsInvalidEventState() {
        assertThatThrownBy(() -> service.updateEvent(UUID.randomUUID(),
                new UpdateEventCommand("X", null, null, null, null), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("event not found");
    }

    // ── updateArea ───────────────────────────────────────────────────────────

    @Test
    void GivenSeatingArea_WhenUpdateAreaName_ThenAreaIsRenamed() {
        Setup s = createPublishedSeatingEvent(2, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand("VIP", null, null), s.callerId());

        assertThat(areaOf(service.getEvent(s.eventId()), s.areaId()).name()).isEqualTo("VIP");
    }

    @Test
    void GivenAreaWithBasePrice_WhenUpdateAreaPrice_ThenGetPriceReflectsNewPrice() {
        Setup s = createPublishedSeatingEvent(3, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, Money.of("25.00", "USD"), null), s.callerId());

        PriceBreakdownDTO q = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(q.basePrice()).isEqualTo(MoneyDTO.from(Money.of("25.00", "USD")));
        assertThat(q.total()).isEqualTo(MoneyDTO.from(Money.of("50.00", "USD")));
    }

    @Test
    void GivenStandingArea_WhenUpdateAreaIncreasesCapacity_ThenAvailableCapacityGrows() {
        Setup s = createPublishedStandingEvent(5, "10.00");

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 10), s.callerId());

        assertThat(areaOf(service.getEvent(s.eventId()), s.areaId()).availableCapacity()).isEqualTo(10);
    }

    @Test
    void GivenStandingAreaWithHeldSeats_WhenShrinkCapacityAboveHeld_ThenHeldQuantityIsPreserved() {
        Setup s = createPublishedStandingEvent(10, "10.00");
        UUID token = UUID.randomUUID();
        eventDomainService.hold(s.eventId(), new HoldCommand(s.areaId(), null, 3, token));

        service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 5), s.callerId());

        EventDTO view = service.getEvent(s.eventId());
        assertThat(areaOf(view, s.areaId()).availableCapacity()).isEqualTo(2);
    }

    @Test
    void GivenStandingAreaWithHeldSeats_WhenShrinkCapacityBelowHeld_ThenThrowsInvalidEventState() {
        Setup s = createPublishedStandingEvent(10, "10.00");
        eventDomainService.hold(s.eventId(), new HoldCommand(s.areaId(), null, 5, UUID.randomUUID()));

        assertThatThrownBy(() -> service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 3), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenSeatingArea_WhenUpdateAreaWithStandingCapacity_ThenThrowsInvalidEventState() {
        Setup s = createPublishedSeatingEvent(2, "10.00");

        assertThatThrownBy(() -> service.updateArea(s.eventId(), s.areaId(),
                new UpdateAreaCommand(null, null, 50), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("standingCapacity");
    }

    @Test
    void GivenUnknownAreaId_WhenUpdateArea_ThenThrowsInvalidEventState() {
        Setup s = createPublishedSeatingEvent(1, "10.00");

        assertThatThrownBy(() -> service.updateArea(s.eventId(), UUID.randomUUID(),
                new UpdateAreaCommand("X", null, null), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    // ── removeArea ───────────────────────────────────────────────────────────

    @Test
    void GivenDraftEventWithArea_WhenRemoveArea_ThenAreaIsRemoved() {
        Setup s = createDraftSeatingEvent(1, "10.00");

        service.removeArea(s.eventId(), s.areaId(), s.callerId());

        assertThat(service.getEvent(s.eventId()).areas()).isEmpty();
    }

    @Test
    void GivenPublishedEvent_WhenRemoveArea_ThenThrowsAndAreaRemains() {
        Setup s = createPublishedSeatingEvent(1, "10.00");

        assertThatThrownBy(() -> service.removeArea(s.eventId(), s.areaId(), s.callerId()))
                .isInstanceOf(InvalidEventStateException.class);

        assertThat(service.getEvent(s.eventId()).areas()).hasSize(1);
    }

    // ── replacePurchasePolicies ──────────────────────────────────────────────

    @Test
    void GivenEvent_WhenReplacePurchasePoliciesWithMaxTickets_ThenOversizedRequestFails() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        service.replacePurchasePolicies(s.eventId(),
                List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(2)), s.callerId());

        PurchaseRequest req = new PurchaseRequest(s.eventId(), s.areaId(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 5, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(s.eventId(), req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void GivenEventWithStrictPolicies_WhenReplaceWithEmpty_ThenPreviouslyRejectedRequestPasses() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID strict = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Strict", "A", Category.OTHER,
                Instant.now().plusSeconds(86400), "V",
                List.of(new MaxTicketsPerOrderPolicy(1), new AgeRestrictionPolicy(18)), null), actor.memberId());

        service.replacePurchasePolicies(strict, List.of(), actor.memberId());

        PurchaseRequest req = new PurchaseRequest(strict, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 99, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(strict, req))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullPolicyList_WhenReplacePurchasePolicies_ThenThrowsNullPointerException() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        assertThatThrownBy(() -> service.replacePurchasePolicies(s.eventId(), null, s.callerId()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenCancelledEvent_WhenReplacePurchasePolicies_ThenThrowsInvalidEventState() {
        Setup s = createPublishedSeatingEvent(1, "10.00");
        service.cancel(s.eventId(), s.callerId());

        List<PurchasePolicyDTO> p = List.of();
        assertThatThrownBy(() -> service.replacePurchasePolicies(s.eventId(), p, s.callerId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    // ── replaceDiscountPolicies ──────────────────────────────────────────────

    @Test
    void GivenEvent_WhenReplaceDiscountPoliciesWith50PercentOff_ThenGetPriceTotalIsHalved() {
        Setup s = createPublishedSeatingEvent(3, "100.00");
        PriceBreakdownDTO before = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(before.total()).isEqualTo(MoneyDTO.from(Money.of("200.00", "USD")));

        DiscountPolicyDTO half = new DiscountPolicyDTO.EarlyBird(
                java.math.BigDecimal.valueOf(50),
                Instant.now().plusSeconds(86400));
        service.replaceDiscountPolicies(s.eventId(), List.of(half), s.callerId());

        PriceBreakdownDTO after = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(after.total()).isEqualTo(MoneyDTO.from(Money.of("100.00", "USD")));
        assertThat(after.discount()).isEqualTo(MoneyDTO.from(Money.of("100.00", "USD")));
    }

    @Test
    void GivenEventWithEarlyBird_WhenReplaceDiscountPoliciesWithEmpty_ThenDiscountIsZero() {
        Setup s = createPublishedSeatingEventWithEarlyBird(2, "50.00", 50);
        PriceBreakdownDTO discounted = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(discounted.discount()).isEqualTo(MoneyDTO.from(Money.of("50.00", "USD")));

        service.replaceDiscountPolicies(s.eventId(), List.of(), s.callerId());

        PriceBreakdownDTO plain = service.getPrice(s.eventId(),
                new PriceQuery(s.areaId(), 2, UUID.randomUUID(), null, null));
        assertThat(plain.discount()).isEqualTo(MoneyDTO.from(Money.of("0.00", "USD")));
    }

    @Test
    void GivenPolicyListContainingNull_WhenReplaceDiscountPolicies_ThenThrowsNullPointerException() {
        Setup s = createDraftSeatingEvent(1, "10.00");
        List<DiscountPolicyDTO> bad = new ArrayList<>();
        bad.add(null);
        assertThatThrownBy(() -> service.replaceDiscountPolicies(s.eventId(), bad, s.callerId()))
                .isInstanceOf(NullPointerException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Setup(UUID eventId, UUID areaId, UUID callerId) {}

    private Setup createDraftSeatingEvent(int seatCount, String price) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), actor.memberId());
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), actor.memberId());
        return new Setup(eventId, areaId, actor.memberId());
    }

    private Setup createPublishedSeatingEvent(int seatCount, String price) {
        Setup s = createDraftSeatingEvent(seatCount, price);
        service.publish(s.eventId(), s.callerId());
        return s;
    }

    private Setup createPublishedStandingEvent(int capacity, String price) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Test Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), actor.memberId());
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of(price, "USD"), AddAreaCommand.AreaType.STANDING, capacity, null), actor.memberId());
        service.publish(eventId, actor.memberId());
        return new Setup(eventId, areaId, actor.memberId());
    }

    private Setup createPublishedSeatingEventWithEarlyBird(int seatCount, String price, int percentOff) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        IEventDiscountPolicy earlyBird = new EarlyBirdDiscountPolicy(
                java.math.BigDecimal.valueOf(percentOff),
                Instant.now().plusSeconds(86400));
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "EB", "A", Category.CONCERT,
                Instant.now().plusSeconds(86400), "V", null, List.of(earlyBird)), actor.memberId());
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), actor.memberId());
        service.publish(eventId, actor.memberId());
        return new Setup(eventId, areaId, actor.memberId());
    }

    private static EventDTO.AreaView areaOf(EventDTO view, UUID areaId) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow();
    }
}
