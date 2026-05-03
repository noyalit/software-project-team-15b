package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventCommand(
        UUID companyId,
        String name,
        String artist,
        Category category,
        Instant startsAt,
        String location,
        List<IEventPurchasePolicy> purchasePolicies,
        List<IEventDiscountPolicy> discountPolicies
) {}
