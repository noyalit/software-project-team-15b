package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite: all children must hold. With no children, {@code test} returns {@code true}
 * (vacuously satisfied). Short-circuits on the first failing child.
 */
public class AndPurchasePolicy implements IEventPurchasePolicy, ICompanyPurchasePolicy {

    @JsonProperty("children")
    private final List<IPurchasePolicy> children;

    @JsonCreator
    public AndPurchasePolicy(@JsonProperty("children") List<IPurchasePolicy> children) {
        Objects.requireNonNull(children, "children");
        List<IPurchasePolicy> copy = new ArrayList<>(children);
        copy.forEach(c -> Objects.requireNonNull(c, "child"));
        this.children = copy;
    }

    public List<IPurchasePolicy> children() { return Collections.unmodifiableList(children); }

    @Override
    public boolean test(PolicyContext ctx) {
        for (IPurchasePolicy child : children) {
            if (!child.test(ctx)) return false;
        }
        return true;
    }

    @Override
    public void validate(PurchaseRequest request, Event event) {
        validateAt(PolicyContext.of(request, event));
    }

    @Override
    public void validate(PurchaseRequest request, Company company) {
        validateAt(PolicyContext.of(request, company));
    }

    private void validateAt(PolicyContext ctx) {
        for (IPurchasePolicy child : children) {
            if (!child.test(ctx)) {
                throw new PolicyViolationException("AND child failed: " + child.label());
            }
        }
    }
}
