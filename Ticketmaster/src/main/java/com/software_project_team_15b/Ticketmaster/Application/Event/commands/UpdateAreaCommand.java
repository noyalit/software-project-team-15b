package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

/**
 * Partial-update payload for {@code IEventManagementService.updateArea}.
 * Null fields are left unchanged. {@code standingCapacity} only applies to
 * standing areas.
 */
public record UpdateAreaCommand(
        String name,
        Money basePrice,
        Integer standingCapacity
) {}
