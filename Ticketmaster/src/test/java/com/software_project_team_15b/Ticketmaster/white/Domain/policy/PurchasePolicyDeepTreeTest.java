package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
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
import com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Deep-tree integration tests for purchase policies. Builds 4-level mixed AND/OR composites
 * for the Company and Event sides, then verifies the boolean verdict along each branch
 * of the tree under sunny / rainy / bad conditions, and through the Event aggregate.
 *
 * <p>Shared business tree (used as both Company and Event roots in different tests):
 * <pre>
 * AND (root)
 *  ├── MinTickets(1)                                              -- leaf
 *  └── OR
 *       ├── AND  (B2B path)
 *       │    ├── MinAge(21)
 *       │    └── MaxTickets(20)
 *       └── AND  (consumer path)
 *            ├── MinAge(13)
 *            ├── MaxTickets(4)
 *            └── OR
 *                 ├── MinAge(18)
 *                 └── MinTickets(2)         -- "group of 2+" override
 * </pre>
 */
class PurchasePolicyDeepTreeTest {

    private static PurchaseRequest req(int quantity, LocalDate birth) {
        return new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birth, quantity, List.of(), null);
    }

    private static PolicyContext ctx(PurchaseRequest r) {
        return new PolicyContext(r, null, null, Instant.now());
    }

    /** The shared 4-level tree described above. */
    private static AndPurchasePolicy buildDeepTree() {
        AndPurchasePolicy b2bBranch = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(21),
                new MaxTicketsRule(20)));

        OrPurchasePolicy consumerInner = new OrPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18),
                new MinTicketsRule(2)));

        AndPurchasePolicy consumerBranch = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(13),
                new MaxTicketsRule(4),
                consumerInner));

        OrPurchasePolicy pathChoice = new OrPurchasePolicy(List.<IPurchasePolicy>of(
                b2bBranch,
                consumerBranch));

        return new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinTicketsRule(1),
                pathChoice));
    }

    // ====================================================================================
    // SUNNY — happy paths through each branch
    // ====================================================================================

    /** SUNNY: adult buying 4 tickets — satisfies consumer branch via MinAge(18) at L4. */
    @Test
    void deep_tree_sunny_adult_consumer_passes_via_l4_minage_branch() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(4, LocalDate.now().minusYears(30));

        assertThat(tree.test(ctx(r))).isTrue();
    }

    /** SUNNY: 14-year-old buying 3 tickets — satisfies consumer branch via MinTickets(2) at L4
     * (fails the L4 MinAge(18) but the OR-sibling MinTickets(2) holds). */
    @Test
    void deep_tree_sunny_teen_group_passes_via_l4_mintickets_branch() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(3, LocalDate.now().minusYears(14));

        assertThat(tree.test(ctx(r))).isTrue();
    }

    /** SUNNY: B2B buyer (age 25) buying 15 tickets — satisfies the B2B branch at L2 (exceeds
     * the consumer cap of 4, but B2B's MaxTickets(20) allows it). */
    @Test
    void deep_tree_sunny_b2b_path_allows_above_consumer_cap() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(15, LocalDate.now().minusYears(25));

        assertThat(tree.test(ctx(r))).isTrue();
    }

    // ====================================================================================
    // RAINY — boundary and partial-branch scenarios
    // ====================================================================================

    /** RAINY: 13-year-old buying exactly 2 — sits on every boundary (MinAge=13, MaxTickets=4,
     * MinTickets=2 saves the inner OR since MinAge(18) fails). */
    @Test
    void deep_tree_rainy_boundary_age_13_qty_2_satisfies_consumer_branch() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(2, LocalDate.now().minusYears(13));

        assertThat(tree.test(ctx(r))).isTrue();
    }

    /** RAINY: adult buying 20 — B2B path passes at its boundary (MaxTickets=20),
     * consumer path fails (MaxTickets=4); OR holds via B2B. */
    @Test
    void deep_tree_rainy_b2b_boundary_at_max_cap_holds() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(20, LocalDate.now().minusYears(40));

        assertThat(tree.test(ctx(r))).isTrue();
    }

    /** RAINY: adult buying exactly 21 — both branches fail their MaxTickets cap, OR fails,
     * root AND fails. Just-above-boundary case. */
    @Test
    void deep_tree_rainy_just_above_b2b_cap_fails_entire_tree() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(21, LocalDate.now().minusYears(40));

        assertThat(tree.test(ctx(r))).isFalse();
    }

    // ====================================================================================
    // BAD — invalid / out-of-spec inputs
    // ====================================================================================

    /** BAD: 10-year-old buying 1 — fails consumer MinAge(13) AND fails B2B MinAge(21);
     * deep OR has no surviving branch. */
    @Test
    void deep_tree_bad_underage_no_branch_survives() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(1, LocalDate.now().minusYears(10));

        assertThat(tree.test(ctx(r))).isFalse();
        assertThatThrownBy(() -> tree.validate(r, (Event) null))
                .isInstanceOf(PolicyViolationException.class);
    }

    /** BAD: a 16-year-old buying 1 — consumer branch holds MinAge(13) ✓ and MaxTickets(4) ✓
     * but the L4 OR fails (MinAge(18) ✗ and MinTickets(2) ✗ since qty=1). Whole tree fails. */
    @Test
    void deep_tree_bad_l4_or_fails_brings_down_consumer_branch() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(1, LocalDate.now().minusYears(16));

        assertThat(tree.test(ctx(r))).isFalse();
    }

    /** BAD: missing birth date — the L4 MinAge(18) inside the consumer's inner OR returns
     * false (null birth → not satisfied). With qty=1, MinTickets(2) also fails → inner OR fails
     * → consumer branch fails. B2B branch also requires age and fails. Root AND fails. */
    @Test
    void deep_tree_bad_null_birthdate_collapses_every_age_branch() {
        AndPurchasePolicy tree = buildDeepTree();
        PurchaseRequest r = req(1, null);

        assertThat(tree.test(ctx(r))).isFalse();
    }

    // ====================================================================================
    // Cross-aggregate integration — Company tree × Event tree, both deep
    // ====================================================================================

    /** SUNNY: Company tree = deep tree; Event tree = simple `And(MaxTickets(4))`.
     * Adult buyer with qty 3 satisfies both. */
    @Test
    void integration_sunny_deep_company_tree_combined_with_event_cap() {
        AndPurchasePolicy companyRoot = buildDeepTree();
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(3, LocalDate.now().minusYears(30));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(eventRoot.test(ctx(r))).isTrue();
        assertThat(companyRoot.test(ctx(r)) && eventRoot.test(ctx(r))).isTrue();
    }

    /** RAINY: B2B path satisfies the Company tree (qty 15, age 30) but the Event sets a
     * tighter cap of 4 — combined integration fails. */
    @Test
    void integration_rainy_event_tightens_company_b2b_cap() {
        AndPurchasePolicy companyRoot = buildDeepTree();
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MaxTicketsRule(4)));
        PurchaseRequest r = req(15, LocalDate.now().minusYears(30));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(eventRoot.test(ctx(r))).isFalse();
        assertThat(companyRoot.test(ctx(r)) && eventRoot.test(ctx(r))).isFalse();
    }

    /** BAD: both deep trees disagree on a borderline buyer — combined fails.
     * Company permits a 14-year-old group of 3 (consumer + MinTickets override); the Event
     * forbids minors via `And(MinAge(18))`. */
    @Test
    void integration_bad_event_minage_overrides_company_consumer_override() {
        AndPurchasePolicy companyRoot = buildDeepTree();
        AndPurchasePolicy eventRoot = new AndPurchasePolicy(List.<IPurchasePolicy>of(
                new MinAgeRule(18)));
        PurchaseRequest r = req(3, LocalDate.now().minusYears(14));

        assertThat(companyRoot.test(ctx(r))).isTrue();
        assertThat(eventRoot.test(ctx(r))).isFalse();
        assertThat(companyRoot.test(ctx(r)) && eventRoot.test(ctx(r))).isFalse();
    }

    // ====================================================================================
    // Drive the deep tree through a real Event aggregate
    // ====================================================================================

    /** SUNNY: deep tree attached to an Event — `Event.purchasePolicies().get(0).test(...)`
     * exercises every level under PolicyContext.of(req, event). */
    @Test
    void event_aggregate_drives_deep_tree_under_event_context() {
        Event event = EventTestFixtures.published(EventTestFixtures.seatingArea(5, "10.00"));
        AndPurchasePolicy tree = buildDeepTree();
        event.replacePurchasePolicies(List.<IEventPurchasePolicy>of(tree));

        PurchaseRequest sunny = new PurchaseRequest(
                event.eventId(), event.areas().get(0).areaId(), UUID.randomUUID(),
                LocalDate.now().minusYears(25), 4, List.of(), null);
        PurchaseRequest bad = new PurchaseRequest(
                event.eventId(), event.areas().get(0).areaId(), UUID.randomUUID(),
                LocalDate.now().minusYears(10), 1, List.of(), null);

        IEventPurchasePolicy attached = event.purchasePolicies().get(0);
        assertThat(attached.test(PolicyContext.of(sunny, event))).isTrue();
        assertThat(attached.test(PolicyContext.of(bad, event))).isFalse();
    }
}
