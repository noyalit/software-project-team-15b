package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the catalog-mutability domain methods on {@link Event}. */
class EventCatalogMutabilityTest {

    // ── updateDetails ────────────────────────────────────────────────────────

    @Test
    void updateDetails_patches_only_non_null_fields() {
        Event event = EventTestFixtures.draft();
        String origName = event.name();
        Instant newStart = Instant.now().plusSeconds(7200);

        event.updateDetails(null, "New Artist", null, newStart, null);

        assertThat(event.name()).isEqualTo(origName);
        assertThat(event.artist()).isEqualTo("New Artist");
        assertThat(event.startsAt()).isEqualTo(newStart);
    }

    @Test
    void updateDetails_all_null_is_a_no_op() {
        Event event = EventTestFixtures.draft();
        String n = event.name();
        String a = event.artist();
        Category c = event.category();
        Instant s = event.startsAt();
        String l = event.location();

        event.updateDetails(null, null, null, null, null);

        assertThat(event.name()).isEqualTo(n);
        assertThat(event.artist()).isEqualTo(a);
        assertThat(event.category()).isEqualTo(c);
        assertThat(event.startsAt()).isEqualTo(s);
        assertThat(event.location()).isEqualTo(l);
    }

    @Test
    void updateDetails_works_in_published_state() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);

        event.updateDetails("Renamed", null, null, null, "New Hall");

        assertThat(event.name()).isEqualTo("Renamed");
        assertThat(event.location()).isEqualTo("New Hall");
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void updateDetails_on_cancelled_event_throws() {
        Event event = EventTestFixtures.draft();
        event.cancel();

        assertThatThrownBy(() -> event.updateDetails("X", null, null, null, null))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void updateDetails_blank_string_throws() {
        Event event = EventTestFixtures.draft();
        assertThatThrownBy(() -> event.updateDetails("   ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateArea ───────────────────────────────────────────────────────────

    @Test
    void updateArea_renames_and_reprices_seating_area() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);

        event.updateArea(area.areaId(), "VIP", Money.of("99.00", "USD"), null);

        assertThat(area.name()).isEqualTo("VIP");
        assertThat(area.basePrice()).isEqualTo(Money.of("99.00", "USD"));
    }

    @Test
    void updateArea_resizes_standing_area_up() {
        StandingEventArea area = EventTestFixtures.standingArea(5, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);

        event.updateArea(area.areaId(), null, null, 10);

        assertThat(area.capacity()).isEqualTo(10);
        assertThat(area.availableCapacity()).isEqualTo(10);
    }

    @Test
    void updateArea_resizes_standing_area_down_keeps_held_floor() {
        StandingEventArea area = EventTestFixtures.standingArea(10, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        UUID token = UUID.randomUUID();
        event.holdStanding(area.areaId(), 3, token);

        event.updateArea(area.areaId(), null, null, 5);

        assertThat(area.capacity()).isEqualTo(5);
        assertThat(area.activeHeldQuantity()).isEqualTo(3);
        assertThat(area.availableCapacity()).isEqualTo(2);
    }

    @Test
    void updateArea_shrink_below_held_floor_throws() {
        StandingEventArea area = EventTestFixtures.standingArea(10, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        event.holdStanding(area.areaId(), 5, UUID.randomUUID());

        assertThatThrownBy(() -> event.updateArea(area.areaId(), null, null, 3))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("sold+held");
    }

    @Test
    void updateArea_standing_capacity_on_seating_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);

        assertThatThrownBy(() -> event.updateArea(area.areaId(), null, null, 50))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("standingCapacity");
    }

    @Test
    void updateArea_unknown_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);

        assertThatThrownBy(() -> event.updateArea(UUID.randomUUID(), "X", null, null))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void updateArea_on_cancelled_event_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();

        assertThatThrownBy(() -> event.updateArea(area.areaId(), "X", null, null))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    // ── removeArea ───────────────────────────────────────────────────────────

    @Test
    void removeArea_in_draft_drops_the_area() {
        Event event = EventTestFixtures.draft();
        SeatingEventArea a = EventTestFixtures.seatingArea(1, "10.00");
        SeatingEventArea b = EventTestFixtures.seatingArea(2, "20.00");
        event.addArea(a);
        event.addArea(b);

        event.removeArea(a.areaId());

        assertThat(event.areas()).extracting(EventArea::areaId).containsExactly(b.areaId());
    }

    @Test
    void removeArea_after_publish_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);

        assertThatThrownBy(() -> event.removeArea(area.areaId()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void removeArea_unknown_id_throws() {
        Event event = EventTestFixtures.draft();
        assertThatThrownBy(() -> event.removeArea(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    // ── replacePurchasePolicies / replaceDiscountPolicies ────────────────────

    @Test
    void replacePurchasePolicies_swaps_the_chain() {
        Event event = EventTestFixtures.draft();
        IEventPurchasePolicy newPolicy = (req, ev) -> {};

        event.replacePurchasePolicies(List.of(newPolicy, newPolicy));

        assertThat(event.purchasePolicies()).hasSize(2);
    }

    @Test
    void replacePurchasePolicies_null_throws() {
        Event event = EventTestFixtures.draft();
        assertThatThrownBy(() -> event.replacePurchasePolicies(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void replacePurchasePolicies_null_element_throws() {
        Event event = EventTestFixtures.draft();
        List<IEventPurchasePolicy> bad = new java.util.ArrayList<>();
        bad.add(null);
        assertThatThrownBy(() -> event.replacePurchasePolicies(bad))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void replacePurchasePolicies_empty_is_allowed() {
        Event event = EventTestFixtures.draft();
        assertThatCode(() -> event.replacePurchasePolicies(List.of())).doesNotThrowAnyException();
        assertThat(event.purchasePolicies()).isEmpty();
    }

    @Test
    void replacePurchasePolicies_on_cancelled_event_throws() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        IEventPurchasePolicy noop = (req, ev) -> {};
        assertThatThrownBy(() -> event.replacePurchasePolicies(List.of(noop)))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void replaceDiscountPolicies_swaps_the_chain() {
        Event event = EventTestFixtures.draft();
        IEventDiscountPolicy d = (sub, req) -> sub;
        event.replaceDiscountPolicies(List.of(d));
        assertThat(event.discountPolicies()).containsExactly(d);
    }

    @Test
    void replaceDiscountPolicies_on_cancelled_event_throws() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        IEventDiscountPolicy noop = (sub, req) -> sub;
        assertThatThrownBy(() -> event.replaceDiscountPolicies(List.of(noop)))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("cancelled");
    }

}
