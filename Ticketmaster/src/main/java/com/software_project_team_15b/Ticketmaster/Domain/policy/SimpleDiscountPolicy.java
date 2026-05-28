package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import java.math.BigDecimal;

/**
 * Leaf in the discount tree — a percentage discount with no precondition.
 * Implements both {@link IEventDiscountPolicy} and {@link ICompanyDiscountPolicy} so the
 * same concrete class can attach to either aggregate.
 */
public class SimpleDiscountPolicy implements IEventDiscountPolicy, ICompanyDiscountPolicy {

    @JsonProperty("percent")
    private final BigDecimal percent;

    @JsonCreator
    public SimpleDiscountPolicy(@JsonProperty("percent") BigDecimal percent) {
        IDiscountPolicy.requireValidPercent(percent);
        this.percent = percent;
    }

    public BigDecimal percent() { return percent; }

    @Override
    public Money discount(Money subtotal, PolicyContext ctx) {
        return subtotal.percent(percent);
    }
}
