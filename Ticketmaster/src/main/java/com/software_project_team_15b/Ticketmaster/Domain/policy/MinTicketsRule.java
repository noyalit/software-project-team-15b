package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;

/** Leaf: buyer must request at least {@code min} tickets. */
public class MinTicketsRule implements IEventPurchasePolicy, ICompanyPurchasePolicy {

    @JsonProperty("min")
    private final int min;

    @JsonCreator
    public MinTicketsRule(@JsonProperty("min") int min) {
        if (min < 1) throw new IllegalArgumentException("min must be >= 1");
        this.min = min;
    }

    public int min() { return min; }

    @Override
    public boolean test(PolicyContext ctx) {
        return ctx != null && ctx.request() != null && ctx.request().quantity() >= min;
    }

    @Override
    public void validate(PurchaseRequest request, Event event) { throwIfFalse(request); }

    @Override
    public void validate(PurchaseRequest request, Company company) { throwIfFalse(request); }

    private void throwIfFalse(PurchaseRequest request) {
        if (request.quantity() < min) {
            throw new PolicyViolationException("quantity " + request.quantity() + " below minimum " + min);
        }
    }
}
