package com.software_project_team_15b.Ticketmaster.Domain.policy.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;

/** Condition: at least {@code min} tickets in the purchase. */
public class MinTicketsCondition implements IDiscountCondition {

    @JsonProperty("min")
    private final int min;

    @JsonCreator
    public MinTicketsCondition(@JsonProperty("min") int min) {
        if (min < 1) throw new IllegalArgumentException("min must be >= 1");
        this.min = min;
    }

    public int min() { return min; }

    @Override
    public boolean test(PolicyContext ctx) {
        return ctx != null && ctx.request() != null && ctx.request().quantity() >= min;
    }
}
