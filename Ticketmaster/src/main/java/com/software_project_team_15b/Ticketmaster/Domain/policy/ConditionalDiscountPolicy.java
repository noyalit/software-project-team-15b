package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.IDiscountCondition;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Leaf: a percentage discount gated by a single {@link IDiscountCondition}. Returns the
 * percent of subtotal when the condition holds, zero otherwise. Use composition of
 * conditions externally (via {@code AndPurchasePolicy}-style structures on the condition
 * side) if richer predicates are needed — kept intentionally minimal here per the spec.
 */
public class ConditionalDiscountPolicy implements IEventDiscountPolicy, ICompanyDiscountPolicy {

    @JsonProperty("percent")
    private final BigDecimal percent;

    @JsonProperty("condition")
    private final IDiscountCondition condition;

    @JsonCreator
    public ConditionalDiscountPolicy(
            @JsonProperty("percent") BigDecimal percent,
            @JsonProperty("condition") IDiscountCondition condition) {
        IDiscountPolicy.requireValidPercent(percent);
        this.percent = percent;
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    public BigDecimal percent() { return percent; }
    public IDiscountCondition condition() { return condition; }

    @Override
    public Money discount(Money subtotal, PolicyContext ctx) {
        if (!condition.test(ctx)) return Money.zero(subtotal.currency());
        return subtotal.percent(percent);
    }
}
