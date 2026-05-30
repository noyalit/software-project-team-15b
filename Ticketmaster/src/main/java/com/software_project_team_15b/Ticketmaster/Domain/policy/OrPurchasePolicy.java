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
 * Composite: at least one child must hold. With no children, {@code test} returns
 * {@code false} (an empty disjunction is unsatisfiable).
 */
public class OrPurchasePolicy implements IEventPurchasePolicy, ICompanyPurchasePolicy {

    @JsonProperty("children")
    private final List<IPurchasePolicy> children;

    @JsonCreator
    public OrPurchasePolicy(@JsonProperty("children") List<IPurchasePolicy> children) {
        Objects.requireNonNull(children, "children");
        List<IPurchasePolicy> copy = new ArrayList<>(children);
        copy.forEach(c -> Objects.requireNonNull(c, "child"));
        this.children = copy;
    }

    public List<IPurchasePolicy> children() { return Collections.unmodifiableList(children); }

    @Override
    public boolean test(PolicyContext ctx) {
        if (children.isEmpty()) return false;
        for (IPurchasePolicy child : children) {
            if (child.test(ctx)) return true;
        }
        return false;
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
        if (!test(ctx)) {
            StringBuilder sb = new StringBuilder("OR composite failed; none of [");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(children.get(i).label());
            }
            sb.append("] held");
            throw new PolicyViolationException(sb.toString());
        }
    }
}
