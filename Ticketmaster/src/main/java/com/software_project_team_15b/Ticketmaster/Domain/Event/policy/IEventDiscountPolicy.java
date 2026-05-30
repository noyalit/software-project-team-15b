package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;

/**
 * Marker subtype of the shared {@link IDiscountPolicy} for policies attachable to an
 * {@code Event} aggregate. Adds no new members — exists so the Event aggregate can keep
 * a strongly-typed collection without coupling to company-level policies.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IEventDiscountPolicy extends IDiscountPolicy {
}
