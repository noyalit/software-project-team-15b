package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Company.DiscountCombineStrategy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MinTicketsCondition;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.TimeWindowCondition;
import com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Deep-tree integration tests for discount policies. Builds 4-level nested composites
 * mixing {@link SumDiscountPolicy}, {@link MaxDiscountPolicy}, and
 * {@link ConditionalDiscountPolicy} leaves, then verifies that the tree returns the
 * expected discount amount on each branch, that the Company-side and Event-side trees
 * combine correctly via {@link DiscountCombineStrategy}, and that the same trees drive
 * the right amounts when attached to an {@link Event} aggregate.
 *
 * <p>Reference tree (the "company" tree in cross-aggregate tests):
 * <pre>
 * Sum (root, stacks)
 *  ├── Simple(5%)                                       -- always-on loyalty
 *  ├── Max                                              -- best-of promo
 *  │    ├── EarlyBird(20%, until +1d)
 *  │    ├── Coupon("VIP", 25%)
 *  │    └── Conditional(15%, MinTickets ≥ 4)            -- bulk
 *  └── Conditional(10%, TimeWindow [now-1h, now+1h])    -- flash window
 * </pre>
 *
 * <p>Event-side counterpart (the "event" tree):
 * <pre>
 * Max (root, no-stacking)
 *  ├── Sum(Simple(8%), Simple(2%))                       -- house combo = 10%
 *  └── Conditional(30%, MinTickets ≥ 5)
 * </pre>
 */
class DiscountPolicyDeepTreeTest {

    private static final String CCY = "USD";

    private static Money usd(String amt) { return Money.of(amt, CCY); }

    private static PurchaseRequest req(int qty, String coupon) {
        return new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), qty, List.of(), coupon);
    }

    private static PolicyContext ctx(PurchaseRequest r) {
        return new PolicyContext(r, null, null, Instant.now());
    }

    /** Build the reference company-side tree. */
    private static SumDiscountPolicy buildCompanyTree() {
        MaxDiscountPolicy bestOfPromo = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().plus(Duration.ofDays(1))),
                new CouponDiscountPolicy("VIP", BigDecimal.valueOf(25)),
                new ConditionalDiscountPolicy(BigDecimal.valueOf(15),
                        new MinTicketsCondition(4))));

        ConditionalDiscountPolicy flashWindow = new ConditionalDiscountPolicy(
                BigDecimal.valueOf(10),
                new TimeWindowCondition(
                        Instant.now().minus(Duration.ofHours(1)),
                        Instant.now().plus(Duration.ofHours(1))));

        return new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(5)),
                bestOfPromo,
                flashWindow));
    }

    /** Build the reference event-side tree. */
    private static MaxDiscountPolicy buildEventTree() {
        SumDiscountPolicy houseCombo = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(8)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(2))));

        ConditionalDiscountPolicy bigGroupBonus = new ConditionalDiscountPolicy(
                BigDecimal.valueOf(30),
                new MinTicketsCondition(5));

        return new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                houseCombo,
                bigGroupBonus));
    }

    // ====================================================================================
    // SUNNY — typical branches active simultaneously
    // ====================================================================================

    /** SUNNY: company tree alone, qty=2, no coupon — cascade 5% then EarlyBird 20% (wins Max)
     * then flash 10% on $100 -> 100 * 0.95 * 0.80 * 0.90 = 68.40; discount = 31.60. */
    @Test
    void company_tree_sunny_loyalty_plus_earlybird_plus_flash_stacks_to_31_60() {
        SumDiscountPolicy tree = buildCompanyTree();
        PurchaseRequest r = req(2, null);

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("31.60"));
    }

    /** SUNNY: same tree with the VIP coupon — cascade 5% then coupon 25% (wins Max) then flash 10%
     * on 100 -> 100 * 0.95 * 0.75 * 0.90 = 64.13; discount = 35.88 (rounded). */
    @Test
    void company_tree_sunny_vip_coupon_wins_inner_max() {
        SumDiscountPolicy tree = buildCompanyTree();
        PurchaseRequest r = req(2, "vip");

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("35.88"));
    }

    /** SUNNY: event tree alone with qty=5 — Max(houseCombo 10%, bulk bonus 30%) → 30 off on 100. */
    @Test
    void event_tree_sunny_big_group_beats_house_combo() {
        MaxDiscountPolicy tree = buildEventTree();
        PurchaseRequest r = req(5, null);

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("30.00"));
    }

    // ====================================================================================
    // RAINY — boundary / alternate branches
    // ====================================================================================

    /** RAINY: qty=4 lights the Max(Conditional bulk @15%) — EarlyBird (20%) still wins the Max
     * → cascade Simple(5) → EarlyBird(20) → flash(10) -> 100 * 0.95 * 0.80 * 0.90 = 68.40;
     * discount = 31.60. */
    @Test
    void company_tree_rainy_bulk_branch_active_at_boundary_but_earlybird_still_wins_max() {
        SumDiscountPolicy tree = buildCompanyTree();
        PurchaseRequest r = req(4, null);

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("31.60"));
    }

    /** RAINY: event tree at the exact MinTickets(5) boundary — bulk bonus fires → 30%. */
    @Test
    void event_tree_rainy_boundary_qty_5_activates_big_group_branch() {
        MaxDiscountPolicy tree = buildEventTree();
        PurchaseRequest r = req(5, null);

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("30.00"));
    }

    /** RAINY: event tree just below the bulk boundary (qty=4) — houseCombo (cascade of 8%+2%
     * = 100 * 0.92 * 0.98 = 90.16) wins; discount = 9.84. */
    @Test
    void event_tree_rainy_just_below_bulk_boundary_falls_back_to_house_combo() {
        MaxDiscountPolicy tree = buildEventTree();
        PurchaseRequest r = req(4, null);

        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("9.84"));
    }

    // ====================================================================================
    // BAD — every conditional / coupon branch suppressed
    // ====================================================================================

    /** BAD: build the same company tree but with the EarlyBird window expired AND no coupon
     * AND qty=1 (no bulk) AND a flash window that has already closed. Only the Simple(5%)
     * survives → 5 off on 100. */
    @Test
    void company_tree_bad_all_conditional_branches_suppressed_only_loyalty_survives() {
        MaxDiscountPolicy bestOfPromo = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().minus(Duration.ofDays(1))),                  // expired
                new CouponDiscountPolicy("VIP", BigDecimal.valueOf(25)),
                new ConditionalDiscountPolicy(BigDecimal.valueOf(15),
                        new MinTicketsCondition(4))));
        ConditionalDiscountPolicy expiredFlash = new ConditionalDiscountPolicy(
                BigDecimal.valueOf(10),
                new TimeWindowCondition(
                        Instant.now().minus(Duration.ofDays(2)),
                        Instant.now().minus(Duration.ofDays(1))));                  // closed
        SumDiscountPolicy tree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(5)),
                bestOfPromo,
                expiredFlash));

        PurchaseRequest r = req(1, null);
        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("5.00"));
    }

    /** BAD: event tree where every leaf is suppressed — empty inner Sum (0%) and bulk branch
     * inactive (qty=1). Max yields 0. */
    @Test
    void event_tree_bad_all_branches_inactive_yields_zero() {
        MaxDiscountPolicy tree = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new SumDiscountPolicy(List.<IDiscountPolicy>of()),
                new ConditionalDiscountPolicy(BigDecimal.valueOf(30),
                        new MinTicketsCondition(5))));

        PurchaseRequest r = req(1, null);
        Money discount = tree.discount(usd("100.00"), ctx(r));

        assertThat(discount).isEqualTo(usd("0.00"));
    }

    // ====================================================================================
    // Cross-aggregate integration with both deep trees
    // ====================================================================================

    /** SUNNY combined: SUM strategy stacks both deep trees.
     * Company tree on $100 with qty=2, no coupon → 5 + 20 + 10 = 35 off.
     * Event tree on $100 with qty=2 → houseCombo 10 wins inside Max (bulk inactive).
     * SUM strategy → 35 + 10 = 45 off; final price $55. */
    @Test
    void integration_sunny_sum_strategy_stacks_both_deep_trees() {
        SumDiscountPolicy companyTree = buildCompanyTree();
        MaxDiscountPolicy eventTree = buildEventTree();
        Money subtotal = usd("100.00");
        PurchaseRequest r = req(2, null);

        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx(r)), subtotal);
        Money eventDiscount   = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(companyDiscount).isEqualTo(usd("31.60"));
        assertThat(eventDiscount).isEqualTo(usd("9.84"));
        assertThat(total).isEqualTo(usd("41.44"));
        assertThat(subtotal.subtract(total)).isEqualTo(usd("58.56"));
    }

    /** RAINY combined: same scenario but the company switches to MAX strategy — picks the
     * larger of the two trees only (35 vs 10) → 35 off, final $65. */
    @Test
    void integration_rainy_max_strategy_picks_company_over_event() {
        SumDiscountPolicy companyTree = buildCompanyTree();
        MaxDiscountPolicy eventTree = buildEventTree();
        Money subtotal = usd("100.00");
        PurchaseRequest r = req(2, null);

        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx(r)), subtotal);
        Money eventDiscount   = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.MAX.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(total).isEqualTo(usd("31.60"));
        assertThat(subtotal.subtract(total)).isEqualTo(usd("68.40"));
    }

    /** RAINY combined: qty=5 activates the event's bulk bonus (30%) which now beats the
     * company cascade (31.60) under MAX strategy by a small margin. Asserts both strategies. */
    @Test
    void integration_rainy_qty_5_changes_event_winner_inside_max_root() {
        SumDiscountPolicy companyTree = buildCompanyTree();
        MaxDiscountPolicy eventTree = buildEventTree();
        Money subtotal = usd("100.00");
        PurchaseRequest r = req(5, null);

        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx(r)), subtotal);
        Money eventDiscount   = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx(r)), subtotal);

        assertThat(companyDiscount).isEqualTo(usd("31.60"));
        assertThat(eventDiscount).isEqualTo(usd("30.00"));
        assertThat(DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal))
                .isEqualTo(usd("61.60"));
        assertThat(DiscountCombineStrategy.MAX.combine(eventDiscount, companyDiscount, subtotal))
                .isEqualTo(usd("31.60"));
    }

    /** BAD combined: build two extreme deep trees that would each return >50% off, then stack
     * under SUM. With both trees clamping individually and the combine clamping the sum, the
     * final discount must equal the subtotal (final price = 0), not exceed it. */
    @Test
    void integration_bad_overlapping_deep_trees_clamp_to_subtotal_floor_zero() {
        SumDiscountPolicy companyTree = new SumDiscountPolicy(List.<IDiscountPolicy>of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(40)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(40))));
        MaxDiscountPolicy eventTree = new MaxDiscountPolicy(List.<IDiscountPolicy>of(
                new SumDiscountPolicy(List.<IDiscountPolicy>of(
                        new SimpleDiscountPolicy(BigDecimal.valueOf(60)),
                        new SimpleDiscountPolicy(BigDecimal.valueOf(60))))));

        Money subtotal = usd("100.00");
        PurchaseRequest r = req(1, null);
        Money companyDiscount = IDiscountPolicy.clamp(companyTree.discount(subtotal, ctx(r)), subtotal);
        Money eventDiscount   = IDiscountPolicy.clamp(eventTree.discount(subtotal, ctx(r)), subtotal);
        Money total = DiscountCombineStrategy.SUM.combine(eventDiscount, companyDiscount, subtotal);

        assertThat(total).isEqualTo(subtotal);
        assertThat(subtotal.subtract(total)).isEqualTo(usd("0.00"));
    }

    // ====================================================================================
    // Drive the deep tree through a real Event aggregate
    // ====================================================================================

    /** SUNNY: deep tree attached to an Event yields the same per-area discount via
     * {@code Event.discountAmountFor}. Base $50 × qty 5 = $250 subtotal; the event tree fires
     * the bulk-bonus branch (30%) → $75 discount. */
    @Test
    void event_aggregate_drives_deep_tree_for_bulk_purchase() {
        SeatingEventArea area = EventTestFixtures.seatingArea(10, "50.00");
        Event event = EventTestFixtures.published(area);
        MaxDiscountPolicy tree = buildEventTree();
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(tree));

        PurchaseRequest r = new PurchaseRequest(
                event.eventId(), area.areaId(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 5, List.of(), null);

        Money discount = event.discountAmountFor(area.areaId(), 5, r);
        Money cheapest = event.cheapestPriceFor(area.areaId(), 5, r);

        assertThat(discount).isEqualTo(usd("75.00"));
        assertThat(cheapest).isEqualTo(usd("175.00"));
    }

    /** RAINY: same setup but qty=2 — bulk branch inactive; houseCombo cascade (8%+2%) wins:
     * 100 * 0.92 * 0.98 = 90.16; discount = 9.84. */
    @Test
    void event_aggregate_drives_deep_tree_below_bulk_boundary() {
        SeatingEventArea area = EventTestFixtures.seatingArea(10, "50.00");
        Event event = EventTestFixtures.published(area);
        MaxDiscountPolicy tree = buildEventTree();
        event.replaceDiscountPolicies(List.<IEventDiscountPolicy>of(tree));

        PurchaseRequest r = new PurchaseRequest(
                event.eventId(), area.areaId(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 2, List.of(), null);

        Money discount = event.discountAmountFor(area.areaId(), 2, r);

        assertThat(discount).isEqualTo(usd("9.84"));
    }
}
