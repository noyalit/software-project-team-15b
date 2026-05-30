package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.AndPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for purchase-policy trees and their cross-aggregate combination.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>the Company-side purchase tree, evaluated in isolation, yields the correct boolean</li>
 *   <li>the Event-side purchase tree, evaluated in isolation, yields the correct boolean</li>
 *   <li>when both trees are evaluated together against the same {@link PurchaseRequest} the
 *       combined "AND" of the two roots produces the expected sunny/rainy/bad-day verdicts.</li>
 * </ul>
 */
class PurchasePolicyIntegrationTest {

    private static PurchaseRequest req(int quantity, LocalDate birth) {
        return new PurchaseRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                birth,
                quantity,
                List.of(),
                null);
    }

    private static PolicyContext ctx(PurchaseRequest r) {
        return new PolicyContext(r, null, null, Instant.now());
    }

    private static boolean both(ICompanyPurchasePolicy company,
                                IEventPurchasePolicy event,
                                PolicyContext c) {
        return company.test(c) && event.test(c);
    }

    // ====================================================================================
    // 1) Company tree in isolation
    // ====================================================================================

    /** SUNNY: AND(MinAge 18, MaxTickets 10) — adult buying 4 tickets satisfies both leaves. */
    @Test
    void company_tree_sunny_and_root_passes_when_all_children_hold() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MaxTicketsRule(10)));
        PurchaseRequest r = req(4, LocalDate.now().minusYears(30));

        assertThat(companyRoot.test(ctx(r))).isTrue();
    }

    /** RAINY: OR(MinTickets 5, MinAge 60) — buyer is 30 but requests 6 → first branch passes, OR holds. */
    @Test
    void company_tree_rainy_or_root_passes_when_only_one_child_holds() {
        OrPurchasePolicy companyRoot = new OrPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(5),
                new MinAgeRule(60)));
        PurchaseRequest r = req(6, LocalDate.now().minusYears(30));

        assertThat(companyRoot.test(ctx(r))).isTrue();
    }

    /** BAD: AND(MinAge 18, MaxTickets 10) — minor → AND fails. */
    @Test
    void company_tree_bad_and_root_fails_when_any_child_fails() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MaxTicketsRule(10)));
        PurchaseRequest r = req(2, LocalDate.now().minusYears(10));

        assertThat(companyRoot.test(ctx(r))).isFalse();
        assertThatThrownBy(() -> companyRoot.validate(r, (com.software_project_team_15b.Ticketmaster.Domain.Company.Company) null))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ====================================================================================
    // 2) Event tree in isolation
    // ====================================================================================

    /** SUNNY: AND(MinTickets 1, MaxTickets 4) — buying 3 satisfies both. */
    @Test
    void event_tree_sunny_and_root_passes_within_bounds() {
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(3, LocalDate.now().minusYears(30));

        assertThat(eventRoot.test(ctx(r))).isTrue();
    }

    /** RAINY: AND on the boundary — exactly at the max-tickets cap still passes. */
    @Test
    void event_tree_rainy_passes_exactly_at_upper_bound() {
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(4, LocalDate.now().minusYears(30));

        assertThat(eventRoot.test(ctx(r))).isTrue();
    }

    /** BAD: AND — 5 tickets exceeds the event's MaxTickets(4) cap → AND fails. */
    @Test
    void event_tree_bad_quantity_above_cap_fails() {
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(5, LocalDate.now().minusYears(30));

        assertThat(eventRoot.test(ctx(r))).isFalse();
    }

    // ====================================================================================
    // 3) Combined Company × Event integration
    // ====================================================================================

    /** SUNNY: both trees hold → combined verdict is true. */
    @Test
    void integration_sunny_both_trees_pass() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MaxTicketsRule(10)));
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(3, LocalDate.now().minusYears(25));

        assertThat(both(companyRoot, eventRoot, ctx(r))).isTrue();
    }

    /** RAINY: company OR with a permissive branch + tight event AND → both still hold. */
    @Test
    void integration_rainy_company_or_holds_via_one_branch_event_and_holds() {
        OrPurchasePolicy companyRoot = new OrPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(99),   // would fail
                new MinTicketsRule(1) // saves the OR
        ));
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(2, LocalDate.now().minusYears(25));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(eventRoot.test(ctx(r))).isTrue();
        assertThat(both(companyRoot, eventRoot, ctx(r))).isTrue();
    }

    /** BAD: event tree holds but company AND fails (minor) → combined fails. */
    @Test
    void integration_bad_company_age_fails_even_when_event_tree_holds() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MaxTicketsRule(10)));
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(2, LocalDate.now().minusYears(10));

        assertThat(companyRoot.test(ctx(r))).isFalse();
        assertThat(eventRoot.test(ctx(r))).isTrue();
        assertThat(both(companyRoot, eventRoot, ctx(r))).isFalse();
    }

    /** BAD: company tree holds but event MaxTickets fails → combined fails. */
    @Test
    void integration_bad_event_quantity_fails_even_when_company_tree_holds() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MaxTicketsRule(10)));
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(5, LocalDate.now().minusYears(25));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(eventRoot.test(ctx(r))).isFalse();
        assertThat(both(companyRoot, eventRoot, ctx(r))).isFalse();
    }

    /** BAD: empty OR composite is unsatisfiable — combined integration must fail. */
    @Test
    void integration_bad_empty_or_composite_is_unsatisfiable() {
        OrPurchasePolicy companyRoot = new OrPurchasePolicy(List.<IPurchasePolicy>of());
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1)));
        PurchaseRequest r = req(2, LocalDate.now().minusYears(25));

        assertThat(companyRoot.test(ctx(r))).isFalse();
        assertThat(both(companyRoot, eventRoot, ctx(r))).isFalse();
    }

    /** BAD: empty AND composite is vacuously true — so combined verdict depends purely on the other side. */
    @Test
    void integration_rainy_empty_and_composite_is_vacuously_true() {
        AndPurchasePolicy companyRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of());
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MaxTicketsRule(2)));
        PurchaseRequest r = req(2, LocalDate.now().minusYears(25));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(both(companyRoot, eventRoot, ctx(r))).isTrue();
    }
}
