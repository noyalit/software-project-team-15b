package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.AndPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MinTicketsCondition;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.TimeWindowCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompositePolicyTest {

    private static final String CCY = "USD";

    private static Money usd(String amt) { return Money.of(amt, CCY); }

    private static PolicyContext ctxWith(int qty, LocalDate birthDate, String coupon) {
        PurchaseRequest req = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birthDate, qty, List.of(), coupon);
        return new PolicyContext(req, null, null, Instant.now());
    }

    @Test
    void simple_discount_applies_percent_unconditionally() {
        SimpleDiscountPolicy p = new SimpleDiscountPolicy(BigDecimal.valueOf(10));
        assertThat(p.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("10.00"));
    }

    @Test
    void conditional_discount_applies_only_when_condition_holds() {
        ConditionalDiscountPolicy p = new ConditionalDiscountPolicy(
                BigDecimal.valueOf(15), new MinTicketsCondition(2));
        assertThat(p.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("0.00"));
        assertThat(p.discount(usd("100.00"), ctxWith(2, null, null)))
                .isEqualTo(usd("15.00"));
    }

    @Test
    void time_window_condition_respects_absolute_bounds() {
        Instant now = Instant.now();
        TimeWindowCondition open = new TimeWindowCondition(
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        TimeWindowCondition closed = new TimeWindowCondition(
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        PolicyContext c = ctxWith(1, null, null);
        assertThat(open.test(c)).isTrue();
        assertThat(closed.test(c)).isFalse();
    }

    @Test
    void sum_discount_stacks_children_and_clamps_at_subtotal() {
        SumDiscountPolicy sum = new SumDiscountPolicy(List.of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(20))
        ));
        // Cascade: 10% then 20% off 100 -> 100 * 0.9 * 0.8 = 72; discount = 28.
        assertThat(sum.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("28.00"));

        // Cascade: 60% off 100 -> 40; then 60% off 40 -> 16; discount = 84. Two finite-percentage
        // children can never push the discount above the subtotal under cascade.
        SumDiscountPolicy oversized = new SumDiscountPolicy(List.of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(60)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(60))
        ));
        assertThat(oversized.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("84.00"));
    }

    @Test
    void max_discount_picks_largest_single_child() {
        MaxDiscountPolicy max = new MaxDiscountPolicy(List.of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(30)),
                new SimpleDiscountPolicy(BigDecimal.valueOf(20))
        ));
        assertThat(max.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("30.00"));
    }

    @Test
    void nested_max_inside_sum_evaluates_recursively() {
        IDiscountPolicy tree = new SumDiscountPolicy(List.of(
                new SimpleDiscountPolicy(BigDecimal.valueOf(5)),
                new MaxDiscountPolicy(List.of(
                        new SimpleDiscountPolicy(BigDecimal.valueOf(10)),
                        new SimpleDiscountPolicy(BigDecimal.valueOf(20))))
        ));
        // Cascade: 5% then max(10%,20%) = 20% applied to the running 95 -> 95 * 0.8 = 76;
        // discount = 24.
        assertThat(tree.discount(usd("100.00"), ctxWith(1, null, null)))
                .isEqualTo(usd("24.00"));
    }

    @Test
    void and_purchase_short_circuits_on_first_failing_child() {
        IPurchasePolicy tree = new AndPurchasePolicy(List.of(
                new MinAgeRule(18),
                new MaxTicketsRule(2)
        ));
        assertThat(tree.test(ctxWith(2, LocalDate.now().minusYears(25), null))).isTrue();
        assertThat(tree.test(ctxWith(2, LocalDate.now().minusYears(15), null))).isFalse();
        assertThat(tree.test(ctxWith(5, LocalDate.now().minusYears(25), null))).isFalse();
    }

    @Test
    void or_purchase_succeeds_if_any_child_holds() {
        IPurchasePolicy tree = new OrPurchasePolicy(List.of(
                new MaxTicketsRule(2),
                new MinTicketsRule(100)
        ));
        assertThat(tree.test(ctxWith(2, null, null))).isTrue();   // ≤ 2
        assertThat(tree.test(ctxWith(100, null, null))).isTrue(); // ≥ 100
        assertThat(tree.test(ctxWith(50, null, null))).isFalse();
    }

    @Test
    void spec_example_nested_tree_age_and_or_ticket_count() {
        // Spec example: "age >= 18 AND (max 2 OR min 100)"
        IPurchasePolicy tree = new AndPurchasePolicy(List.of(
                new MinAgeRule(18),
                new OrPurchasePolicy(List.of(
                        new MaxTicketsRule(2),
                        new MinTicketsRule(100)))
        ));

        // 1 ticket / age 19 → passes (1 ≤ 2 satisfies OR; age OK)
        assertThat(tree.test(ctxWith(1, LocalDate.now().minusYears(19), null))).isTrue();
        // 1 ticket / age 17 → fails age branch
        assertThat(tree.test(ctxWith(1, LocalDate.now().minusYears(17), null))).isFalse();
        // 3 tickets / age 19 → 3 violates both OR children
        assertThat(tree.test(ctxWith(3, LocalDate.now().minusYears(19), null))).isFalse();
        // 100 tickets / age 19 → MinTickets(100) satisfies OR
        assertThat(tree.test(ctxWith(100, LocalDate.now().minusYears(19), null))).isTrue();
    }

    @Test
    void validate_on_event_purchase_throws_with_useful_message() {
        AndPurchasePolicy tree = new AndPurchasePolicy(List.of(new MinAgeRule(21)));
        PurchaseRequest req = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(18), 1, List.of(), null);

        assertThatThrownBy(() -> tree.validate(req, (com.software_project_team_15b.Ticketmaster.Domain.Event.Event) null))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("MinAgeRule");
    }
}
