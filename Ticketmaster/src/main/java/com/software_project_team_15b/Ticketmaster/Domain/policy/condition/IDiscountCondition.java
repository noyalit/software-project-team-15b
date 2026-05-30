package com.software_project_team_15b.Ticketmaster.Domain.policy.condition;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;

/**
 * Predicate used by {@code ConditionalDiscountPolicy} to gate a percentage discount.
 *
 * <p>Open for extension — add new condition kinds as additional implementations and they
 * automatically work inside any {@code ConditionalDiscountPolicy}. Polymorphism via Jackson's
 * {@code @class} property is preserved across the wire.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IDiscountCondition {
    boolean test(PolicyContext ctx);
}
