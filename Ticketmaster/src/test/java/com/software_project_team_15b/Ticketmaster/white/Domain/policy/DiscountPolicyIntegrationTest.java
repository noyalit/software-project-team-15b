package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Company.DiscountCombineStrategy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for discount-policy trees and their cross-aggregate combination
 * through {@link DiscountCombineStrategy}.
 *
 * <p>Verifies each tree independently and then their fusion under SUM and MAX strategies.
 */
class DiscountPolicyIntegrationTest {

    private static final String CCY = "USD";

    private static Money usd(String amt) { return Money.of(amt, CCY); }

    private static PurchaseRequest req(String coupon) {
        return new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), coupon);
    }

    private static PolicyContext ctx(PurchaseRequest r) {
        return new PolicyContext(r, null, null, Instant.now());
    }

    // ====================================================================================
    // 1) Company tree in isolation (SumDiscountPolicy: stacking)
    // ====================================================================================

    /** SUNNY: SUM cascade 10% then 5% on 100 -> 100 * 0.9 * 0.95 = 85.50; discount = 14.50. */
    @Test
    void company_tree_sunny_sum_stacks_two_simple_discounts() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(5))));

        Money discount = companyRoot.discount(usd("100.00"), ctx(req(null)));

        assertThat(discount).isEqualTo(usd("14.50"));
    }

    /** RAINY: SUM with a coupon-gated child — coupon missing means only the unconditional child contributes. */
    @Test
    void company_tree_rainy_sum_skips_inactive_coupon_child() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new CouponDiscountPolicy("SAVE20", BigDecimal.valueOf(20))));

        Money discount = companyRoot.discount(usd("100.00"), ctx(req(null)));

        assertThat(discount).isEqualTo(usd("10.00"));
    }

    /**
     * BAD: SUM cascade of 50% then 60% on 100 -> 100 * 0.5 * 0.4 = 20 final; discount = 80.
     * Under cascade, two finite percentages can never push the discount above the subtotal,
     * so the per-step clamp keeps it well-defined and bounded.
     */
    @Test
    void company_tree_bad_sum_clamps_when_total_exceeds_subtotal() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(50)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(60))));

        Money discount = companyRoot.discount(usd("100.00"), ctx(req(null)));

        assertThat(discount).isEqualTo(usd("80.00"));
    }

    // ====================================================================================
    // 2) Event tree in isolation (MaxDiscountPolicy: no stacking)
    // ====================================================================================

    /** SUNNY: MAX(20% early-bird active + 10% coupon active) → 20 discount wins. */
    @Test
    void event_tree_sunny_max_picks_largest_active_child() {
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().plus(Duration.ofDays(1))),
                new CouponDiscountPolicy("SAVE10", BigDecimal.valueOf(10))));

        Money discount = eventRoot.discount(usd("100.00"), ctx(req("save10")));

        assertThat(discount).isEqualTo(usd("20.00"));
    }

    /** RAINY: MAX where the larger discount is inactive (early-bird window closed) — smaller active wins. */
    @Test
    void event_tree_rainy_max_falls_back_to_active_child() {
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(30),
                        Instant.now().minus(Duration.ofDays(1))),  // expired
                new CouponDiscountPolicy("SAVE10", BigDecimal.valueOf(10))));

        Money discount = eventRoot.discount(usd("100.00"), ctx(req("save10")));

        assertThat(discount).isEqualTo(usd("10.00"));
    }

    /** BAD: MAX where no child is active → 0 discount. */
    @Test
    void event_tree_bad_no_active_child_yields_zero() {
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(30),
                        Instant.now().minus(Duration.ofDays(1))),
                new CouponDiscountPolicy("SAVE10", BigDecimal.valueOf(10))));

        Money discount = eventRoot.discount(usd("100.00"), ctx(req(null)));

        assertThat(discount).isEqualTo(usd("0.00"));
    }

    // ====================================================================================
    // 3) Combined Company × Event integration via DiscountCombineStrategy
    // ====================================================================================

    /** SUNNY: SUM strategy stacks the two trees — company(15) + event(20) = 35 off on 100. */
    @Test
    void integration_sunny_sum_strategy_stacks_company_and_event_discounts() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(5))));
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().plus(Duration.ofDays(1)))));

        Money subtotal = usd("100.00");
        PurchaseRequest r = req(null);
        Money eventDiscount  = IDiscountPolicy.clamp(eventRoot.discount(subtotal, ctx(r)), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyRoot.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(eventDiscount).isEqualTo(usd("20.00"));
        assertThat(companyDiscount).isEqualTo(usd("14.50"));
        assertThat(total).isEqualTo(usd("34.50"));
        assertThat(subtotal.subtract(total)).isEqualTo(usd("65.50"));
    }

    /** SUNNY: MAX strategy returns whichever tree gives the larger discount. */
    @Test
    void integration_sunny_max_strategy_picks_larger_of_two_trees() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(15))));
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().plus(Duration.ofDays(1)))));

        Money subtotal = usd("100.00");
        PurchaseRequest r = req(null);
        Money eventDiscount  = IDiscountPolicy.clamp(eventRoot.discount(subtotal, ctx(r)), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyRoot.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.MAX.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(total).isEqualTo(usd("20.00"));
    }

    /** RAINY: SUM strategy where one tree contributes zero — total equals the other tree's discount. */
    @Test
    void integration_rainy_sum_strategy_with_inactive_event_tree() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10))));
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new CouponDiscountPolicy("ABSENT", BigDecimal.valueOf(50))));

        Money subtotal = usd("200.00");
        PurchaseRequest r = req(null);
        Money eventDiscount  = IDiscountPolicy.clamp(eventRoot.discount(subtotal, ctx(r)), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyRoot.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(eventDiscount).isEqualTo(usd("0.00"));
        assertThat(companyDiscount).isEqualTo(usd("20.00"));
        assertThat(total).isEqualTo(usd("20.00"));
    }

    /** BAD: combined SUM where each tree would be 60% — stacked 120 exceeds subtotal → clamped to subtotal. */
    @Test
    void integration_bad_sum_strategy_clamps_to_subtotal_on_over_discount() {
        SumDiscountPolicy companyRoot = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(60))));
        MaxDiscountPolicy eventRoot = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(60))));

        Money subtotal = usd("100.00");
        PurchaseRequest r = req(null);
        Money eventDiscount  = IDiscountPolicy.clamp(eventRoot.discount(subtotal, ctx(r)), subtotal);
        Money companyDiscount = IDiscountPolicy.clamp(companyRoot.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(total).isEqualTo(subtotal);
        assertThat(subtotal.subtract(total)).isEqualTo(usd("0.00"));
    }
}
