package com.software_project_team_15b.Ticketmaster.Domain.policy.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.time.Instant;

/**
 * Condition: the moment of purchase ({@code ctx.now()}) falls inside the absolute
 * window {@code [from, to]}. Either bound may be {@code null} for an open-ended window.
 * Both bounds are inclusive.
 */
public class TimeWindowCondition implements IDiscountCondition {

    @JsonProperty("from")
    private final Instant from;

    @JsonProperty("to")
    private final Instant to;

    @JsonCreator
    public TimeWindowCondition(
            @JsonProperty("from") Instant from,
            @JsonProperty("to") Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        this.from = from;
        this.to = to;
    }

    public Instant from() { return from; }
    public Instant to() { return to; }

    @Override
    public boolean test(PolicyContext ctx) {
        Instant now = ctx != null && ctx.now() != null ? ctx.now() : Instant.now();
        if (from != null && now.isBefore(from)) return false;
        if (to != null && now.isAfter(to)) return false;
        return true;
    }
}
