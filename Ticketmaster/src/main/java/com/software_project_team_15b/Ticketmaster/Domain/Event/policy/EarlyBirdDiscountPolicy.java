package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Percentage discount for purchases made before {@code until}. Kept as a stand-alone class
 * (rather than re-expressed via {@code ConditionalDiscountPolicy(percent, TimeWindowCondition)})
 * so previously serialized rows continue to deserialize without migration.
 */
public class EarlyBirdDiscountPolicy implements IEventDiscountPolicy, ICompanyDiscountPolicy {

    @JsonProperty("percentage")
    private final BigDecimal percentage;

    @JsonProperty("until")
    private final Instant until;

    @JsonCreator
    public EarlyBirdDiscountPolicy(
            @JsonProperty("percentage") BigDecimal percentage,
            @JsonProperty("until") Instant until) {
        IDiscountPolicy.requireValidPercent(percentage);
        this.percentage = percentage;
        this.until = Objects.requireNonNull(until, "until");
    }

    public BigDecimal percentage() { return percentage; }
    public Instant until() { return until; }

    @Override
    public Money discount(Money subtotal, PolicyContext ctx) {
        Instant now = ctx != null && ctx.now() != null ? ctx.now() : Instant.now();
        if (now.isBefore(until)) {
            return subtotal.percent(percentage);
        }
        return Money.zero(subtotal.currency());
    }
}
