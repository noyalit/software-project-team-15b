package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;

/** Leaf: buyer may request at most {@code max} tickets. */
public class MaxTicketsRule implements IEventPurchasePolicy, ICompanyPurchasePolicy {

    @JsonProperty("max")
    private final int max;

    @JsonCreator
    public MaxTicketsRule(@JsonProperty("max") int max) {
        if (max < 1) throw new IllegalArgumentException("max must be >= 1");
        this.max = max;
    }

    public int max() { return max; }

    @Override
    public boolean test(PolicyContext ctx) {
        return ctx != null && ctx.request() != null && ctx.request().quantity() <= max;
    }

    @Override
    public void validate(PurchaseRequest request, Event event) { throwIfFalse(request); }

    @Override
    public void validate(PurchaseRequest request, Company company) { throwIfFalse(request); }

    private void throwIfFalse(PurchaseRequest request) {
        if (request.quantity() > max) {
            throw new PolicyViolationException("quantity " + request.quantity() + " exceeds maximum " + max);
        }
    }
}
