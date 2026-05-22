package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;

/**
 * Marker subtype of the shared {@link IPurchasePolicy} for policies attachable to an
 * {@code Event} aggregate.
 *
 * <p>Historical concrete impls (e.g. {@code AgeRestrictionPolicy},
 * {@code MaxTicketsPerOrderPolicy}, {@code NoLonelySeatPolicy}) implement only
 * {@link #validate(PurchaseRequest, Event)}. The default {@link #test(PolicyContext)}
 * adapts that throwing signature to the predicate contract by catching
 * {@link PolicyViolationException}. New composite/leaf classes override {@code test}
 * directly and provide their own {@code validate} (which simply throws if {@code test}
 * returns false).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IEventPurchasePolicy extends IPurchasePolicy {

    void validate(PurchaseRequest request, Event event);

    @Override
    default boolean test(PolicyContext ctx) {
        try {
            validate(ctx.request(), ctx.event());
            return true;
        } catch (PolicyViolationException e) {
            return false;
        }
    }
}
