package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite: stacks discounts by summing each child's contribution, clamped at the
 * subtotal — implements the "stacking" rule (כפל הנחות). With no children, returns zero.
 */
public class SumDiscountPolicy implements IEventDiscountPolicy, ICompanyDiscountPolicy {

    @JsonProperty("children")
    private final List<IDiscountPolicy> children;

    @JsonCreator
    public SumDiscountPolicy(@JsonProperty("children") List<IDiscountPolicy> children) {
        Objects.requireNonNull(children, "children");
        List<IDiscountPolicy> copy = new ArrayList<>(children);
        copy.forEach(c -> Objects.requireNonNull(c, "child"));
        this.children = copy;
    }

    public List<IDiscountPolicy> children() { return Collections.unmodifiableList(children); }

    @Override
    public Money discount(Money subtotal, PolicyContext ctx) {
        Money total = Money.zero(subtotal.currency());
        for (IDiscountPolicy child : children) {
            Money d = IDiscountPolicy.clamp(child.discount(subtotal, ctx), subtotal);
            total = total.add(d);
        }
        return IDiscountPolicy.clamp(total, subtotal);
    }
}
