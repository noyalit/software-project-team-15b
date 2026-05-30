package com.software_project_team_15b.Ticketmaster.Domain.policy.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;

/** Condition: at most {@code max} tickets in the purchase. */
public class MaxTicketsCondition implements IDiscountCondition {

    @JsonProperty("max")
    private final int max;

    @JsonCreator
    public MaxTicketsCondition(@JsonProperty("max") int max) {
        if (max < 1) throw new IllegalArgumentException("max must be >= 1");
        this.max = max;
    }

    public int max() { return max; }

    @Override
    public boolean test(PolicyContext ctx) {
        return ctx != null && ctx.request() != null && ctx.request().quantity() <= max;
    }
}
