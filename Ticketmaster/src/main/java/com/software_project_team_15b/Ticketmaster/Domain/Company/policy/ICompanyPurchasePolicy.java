package com.software_project_team_15b.Ticketmaster.Domain.Company.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;

/**
 * Marker subtype of the shared {@link IPurchasePolicy} for policies attachable to a
 * {@code Company} aggregate. See {@code IEventPurchasePolicy} for the same adapter
 * rationale.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface ICompanyPurchasePolicy extends IPurchasePolicy {

    void validate(PurchaseRequest request, Company company);

    @Override
    default boolean test(PolicyContext ctx) {
        try {
            validate(ctx.request(), ctx.company());
            return true;
        } catch (PolicyViolationException e) {
            return false;
        }
    }
}
