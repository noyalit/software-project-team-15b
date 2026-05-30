package com.software_project_team_15b.Ticketmaster.Domain.Company.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;

/**
 * Marker subtype of the shared {@link IDiscountPolicy} for policies attachable to a
 * {@code Company} aggregate.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface ICompanyDiscountPolicy extends IDiscountPolicy {
}
