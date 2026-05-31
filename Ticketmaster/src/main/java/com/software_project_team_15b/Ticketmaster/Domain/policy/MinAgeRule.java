package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import java.time.LocalDate;
import java.time.Period;

/**
 * Leaf purchase rule: buyer must be at least {@code minAge} years old. Mirrors the legacy
 * {@code AgeRestrictionPolicy} but is attachable at both Event and Company levels and
 * exposes the predicate form needed by the And/Or composites.
 */
public class MinAgeRule implements IEventPurchasePolicy, ICompanyPurchasePolicy {

    @JsonProperty("minAge")
    private final int minAge;

    @JsonCreator
    public MinAgeRule(@JsonProperty("minAge") int minAge) {
        if (minAge < 0) throw new IllegalArgumentException("minAge must be >= 0");
        this.minAge = minAge;
    }

    public int minAge() { return minAge; }

    @Override
    public boolean test(PolicyContext ctx) {
        if (ctx == null || ctx.request() == null) return false;
        LocalDate birth = ctx.request().buyerBirthDate();
        if (birth == null) return false;
        return Period.between(birth, LocalDate.now()).getYears() >= minAge;
    }

    @Override
    public void validate(PurchaseRequest request, Event event) {
        throwIfFalse(request);
    }

    @Override
    public void validate(PurchaseRequest request, Company company) {
        throwIfFalse(request);
    }

    private void throwIfFalse(PurchaseRequest request) {
        LocalDate birth = request.buyerBirthDate();
        if (birth == null) {
            throw new PolicyViolationException("buyer birth date required for age-restricted purchase");
        }
        int age = Period.between(birth, LocalDate.now()).getYears();
        if (age < minAge) {
            throw new PolicyViolationException("buyer age " + age + " below minimum " + minAge);
        }
    }
}
