package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.AndPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that exercise discount and purchase policies after they have been
 * attached to an {@link Event} aggregate. Verifies that calling the policy entry points
 * through the Event context (its own discount methods and the purchasePolicies list) drives
 * the assigned trees correctly.
 */
class EventPolicyContextIntegrationTest {

    private static PurchaseRequest req(UUID eventId, UUID areaId, int qty, String coupon) {
        return new PurchaseRequest(eventId, areaId, UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), qty, List.of(), coupon);
    }

    // ====================================================================================
    // Discount policies through Event.discountAmountFor / Event.cheapestPriceFor
    // ====================================================================================

    /** SUNNY: a SumDiscountPolicy attached to the event yields stacked discounts at the area+quantity. */
    @Test
    void event_discount_sunny_sum_tree_drives_total_discount() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "50.00"); // basePrice = $50
        Event event = EventTestFixtures.published(area);
        SumDiscountPolicy tree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(5))));
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(tree));

        // subtotal = $50 * 2 = $100. Cascade: 100 * 0.9 * 0.95 = 85.50 final; discount = 14.50.
        Money discount = event.discountAmountFor(area.areaId(), 2, req(event.eventId(), area.areaId(), 2, null));
        Money cheapest = event.cheapestPriceFor(area.areaId(), 2, req(event.eventId(), area.areaId(), 2, null));

        assertThat(discount).isEqualTo(Money.of("14.50", "USD"));
        assertThat(cheapest).isEqualTo(Money.of("85.50", "USD"));
    }

    /** SUNNY: a Coupon discount only fires when the matching code is in the request. */
    @Test
    void event_discount_sunny_coupon_fires_when_code_matches() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "40.00");
        Event event = EventTestFixtures.published(area);
        CouponDiscountPolicy coupon = new CouponDiscountPolicy("VIP25", BigDecimal.valueOf(25));
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(coupon));

        Money withCode = event.discountAmountFor(area.areaId(), 1, req(event.eventId(), area.areaId(), 1, "vip25"));
        Money withoutCode = event.discountAmountFor(area.areaId(), 1, req(event.eventId(), area.areaId(), 1, null));

        assertThat(withCode).isEqualTo(Money.of("10.00", "USD"));      // 25% of $40
        assertThat(withoutCode).isEqualTo(Money.of("0.00", "USD"));
    }

    /** RAINY: two top-level discount policies on the event — Event.discountAmountFor returns the largest. */
    @Test
    void event_discount_rainy_multiple_top_level_policies_largest_wins() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = EventTestFixtures.published(area);
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(10), Instant.now().plus(Duration.ofDays(1))),
                new CouponDiscountPolicy("BIG30", BigDecimal.valueOf(30))));

        Money withCoupon = event.discountAmountFor(area.areaId(), 1,
                req(event.eventId(), area.areaId(), 1, "big30"));
        Money noCoupon = event.discountAmountFor(area.areaId(), 1,
                req(event.eventId(), area.areaId(), 1, null));

        assertThat(withCoupon).isEqualTo(Money.of("30.00", "USD")); // coupon (30) wins over early-bird (10)
        assertThat(noCoupon).isEqualTo(Money.of("10.00", "USD"));   // only early-bird remains
    }

    /** RAINY: MaxDiscountPolicy as root on the event — picks the largest active leaf. */
    @Test
    void event_discount_rainy_max_root_picks_largest_active_leaf() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = EventTestFixtures.published(area);
        MaxDiscountPolicy max = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(10), Instant.now().plus(Duration.ofDays(1))),
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(40), Instant.now().minus(Duration.ofDays(1)))));
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(max));

        Money discount = event.discountAmountFor(area.areaId(), 1,
                req(event.eventId(), area.areaId(), 1, null));

        assertThat(discount).isEqualTo(Money.of("10.00", "USD"));
    }

    /** BAD: pricing an unknown area through the Event must throw. */
    @Test
    void event_discount_bad_unknown_area_throws() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = EventTestFixtures.published(area);
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10))));

        UUID bogusArea = UUID.randomUUID();
        assertThatThrownBy(() -> event.discountAmountFor(bogusArea, 1,
                req(event.eventId(), bogusArea, 1, null)))
                .isInstanceOf(InvalidEventStateException.class);
    }

    /** BAD: replacing policies on a cancelled event is forbidden. */
    @Test
    void event_discount_bad_replace_on_cancelled_event_throws() {
        Event event = EventTestFixtures.published(EventTestFixtures.seatingArea(2, "10.00"));
        event.cancel();

        assertThatThrownBy(() -> event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)))))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ====================================================================================
    // Purchase policies attached to the Event, evaluated under PolicyContext.of(req, event)
    // ====================================================================================

    /** SUNNY: an AND root attached to the event passes when the request satisfies both leaves. */
    @Test
    void event_purchase_sunny_and_root_passes_via_event_context() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "10.00");
        Event event = EventTestFixtures.published(area);
        AndPurchasePolicy tree = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        event.replacePurchasePolicies(List.<IEventPurchasePolicy>of(tree));

        PurchaseRequest r = req(event.eventId(), area.areaId(), 3, null);
        PolicyContext c = PolicyContext.of(r, event);

        IEventPurchasePolicy attached = event.purchasePolicies().get(0);
        assertThat(attached).isSameAs(tree);
        assertThat(attached.test(c)).isTrue();
    }

    /** RAINY: at the boundary — exactly at MaxTicketsRule's cap — still satisfied via the Event's tree. */
    @Test
    void event_purchase_rainy_boundary_quantity_at_cap_passes() {
        SeatingEventArea area = EventTestFixtures.seatingArea(10, "10.00");
        Event event = EventTestFixtures.published(area);
        AndPurchasePolicy tree = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MaxTicketsRule(4)));
        event.replacePurchasePolicies(List.<IEventPurchasePolicy>of(tree));

        PurchaseRequest r = req(event.eventId(), area.areaId(), 4, null);
        PolicyContext c = PolicyContext.of(r, event);

        assertThat(event.purchasePolicies().get(0).test(c)).isTrue();
    }

    /** BAD: violating the event's attached purchase tree returns false through Event context. */
    @Test
    void event_purchase_bad_violation_returns_false_via_event_context() {
        SeatingEventArea area = EventTestFixtures.seatingArea(10, "10.00");
        Event event = EventTestFixtures.published(area);
        AndPurchasePolicy tree = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MaxTicketsRule(2)));
        event.replacePurchasePolicies(List.<IEventPurchasePolicy>of(tree));

        PurchaseRequest r = req(event.eventId(), area.areaId(), 5, null);
        PolicyContext c = PolicyContext.of(r, event);

        assertThat(event.purchasePolicies().get(0).test(c)).isFalse();
    }
}
