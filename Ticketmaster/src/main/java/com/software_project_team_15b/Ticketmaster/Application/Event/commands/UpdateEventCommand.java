package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import java.time.Instant;

/**
 * Partial-update payload for {@code IEventManagementService.updateEvent}.
 * Null fields are left unchanged; non-null strings must not be blank.
 */
public record UpdateEventCommand(
        String name,
        String artist,
        Category category,
        Instant startsAt,
        String location
) {}
