package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Company.DiscountCombineStrategy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Boundary tests for the final-price floor. After applying combined discounts from both
 * Company-side and Event-side trees, the final price must never go below zero.
 *
 * <p>The floor is enforced at three layers:
 * <ul>
 *   <li>each discount tree clamps its result to the subtotal
 *       (see {@link IDiscountPolicy#clamp})</li>
 *   <li>{@link SumDiscountPolicy} clamps the running sum at every step</li>
 *   <li>{@link DiscountCombineStrategy#SUM} re-clamps the sum of the two trees, and
 *       {@link DiscountCombineStrategy#MAX} returns the larger of two already-clamped values</li>
 * </ul>
 * If any of these tests fail, the corresponding clamp has regressed and must be
 * re-introduced in application code before the boundary can be relied on.
 */
class MinimumPriceBoundaryTest {

    private static final String CCY = "USD";

    private static Money usd(String amt) { return Money.of(amt, CCY); }

    private static PurchaseRequest req() {
        return new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
    }

    private static PolicyContext ctx() {
        return new PolicyContext(req(), null, null, Instant.now());
    }

    // ====================================================================================
    // Sunny day — moderate discounts stay well above zero
    // ====================================================================================

    /** SUNNY: 10% event + 5% company on $100 → final price $85 (>0). */
    @Test
    void boundary_sunny_moderate_discounts_keep_price_above_zero() {
        SumDiscountPolicy eventTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10))));
        SumDiscountPolicy companyTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(5))));

        Money subtotal = usd("100.00");
        Money eventDiscount = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx()), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx()), subtotal);
        Money totalDiscount = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);
        Money finalPrice = subtotal.subtract(totalDiscount);

        assertThat(finalPrice).isEqualTo(usd("85.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    // ====================================================================================
    // Rainy day — discounts exactly meet the subtotal, final price lands on zero
    // ====================================================================================

    /** RAINY: 100% event + 0% company under SUM → discount clamps at subtotal, final = $0. */
    @Test
    void boundary_rainy_sum_strategy_one_side_full_discount_lands_on_zero() {
        SumDiscountPolicy eventTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));
        SumDiscountPolicy companyTree = new SumDiscountPolicy(List.<IDiscountPolicy>of());

        Money subtotal = usd("100.00");
        Money eventDiscount = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx()), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx()), subtotal);
        Money totalDiscount = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);
        Money finalPrice = subtotal.subtract(totalDiscount);

        assertThat(finalPrice).isEqualTo(usd("0.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    /** RAINY: 100% under MAX strategy → cannot stack, final = $0. */
    @Test
    void boundary_rainy_max_strategy_caps_at_full_discount() {
        SumDiscountPolicy eventTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));
        SumDiscountPolicy companyTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(50))));

        Money subtotal = usd("100.00");
        Money eventDiscount = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx()), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx()), subtotal);
        Money totalDiscount = DiscountCombineStrategy.MAX.combine(eventDiscount, companyDiscount, subtotal);
        Money finalPrice = subtotal.subtract(totalDiscount);

        assertThat(finalPrice).isEqualTo(usd("0.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    // ====================================================================================
    // Bad day — over-discounting at both levels must still floor at zero (not go negative)
    // ====================================================================================

    /** BAD: 100% event + 100% company under SUM → combined = 200 but combine() clamps to subtotal → final = $0. */
    @Test
    void boundary_bad_sum_strategy_two_full_discounts_clamp_to_subtotal() {
        SumDiscountPolicy eventTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));
        SumDiscountPolicy companyTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));

        Money subtotal = usd("100.00");
        Money eventDiscount = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx()), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx()), subtotal);
        Money totalDiscount = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);
        Money finalPrice = subtotal.subtract(totalDiscount);

        assertThat(totalDiscount).isEqualTo(subtotal);
        assertThat(finalPrice).isEqualTo(usd("0.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    /**
     * BAD: SumDiscountPolicy with one 100% child — cascade drives the running price to 0,
     * total discount equals the subtotal.
     */
    @Test
    void boundary_bad_sum_tree_with_full_off_child_clamps_to_subtotal() {
        SumDiscountPolicy eventTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(70)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));

        Money subtotal = usd("100.00");
        Money treeDiscount = eventTree.discount(subtotal, ctx());
        Money finalPrice = subtotal.subtract(treeDiscount);

        assertThat(treeDiscount).isEqualTo(subtotal);
        assertThat(finalPrice).isEqualTo(usd("0.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    /** BAD: MaxDiscountPolicy where a child returns more than subtotal — child clamp keeps it at subtotal. */
    @Test
    void boundary_bad_max_tree_oversized_child_clamps_to_subtotal() {
        MaxDiscountPolicy tree = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(50))));

        Money subtotal = usd("80.00");
        Money discount = tree.discount(subtotal, ctx());
        Money finalPrice = subtotal.subtract(discount);

        assertThat(discount).isEqualTo(subtotal);
        assertThat(finalPrice).isEqualTo(usd("0.00"));
        assertThat(finalPrice.isNegative()).isFalse();
    }

    /** BAD: full integration through Event.cheapestPriceFor with a 100% discount — never negative. */
    @Test
    void boundary_bad_event_cheapest_price_floors_at_zero() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "75.00");
        Event event = EventTestFixtures.published(area);
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(100))));

        PurchaseRequest r = new PurchaseRequest(
                event.eventId(), area.areaId(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 2, List.of(), null);
        Money cheapest = event.cheapestPriceFor(area.areaId(), 2, r);

        assertThat(cheapest).isEqualTo(usd("0.00"));
        assertThat(cheapest.isNegative()).isFalse();
    }
}
