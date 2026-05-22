package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Component role in the Composite pattern for purchase-eligibility trees.
 *
 * <p>Implementations are pure predicates over a {@link PolicyContext}. The existing
 * level-specific interfaces ({@code IEventPurchasePolicy}, {@code ICompanyPurchasePolicy})
 * still expose a {@code validate(...)} that throws on failure; new composite/leaf classes
 * implement this shared {@link #test(PolicyContext)} and rely on the level interfaces to
 * adapt to those throwing signatures.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IPurchasePolicy {

    boolean test(PolicyContext ctx);

    /** Human-readable label used when raising a {@code PolicyViolationException}. */
    default String label() {
        return getClass().getSimpleName();
    }
}
