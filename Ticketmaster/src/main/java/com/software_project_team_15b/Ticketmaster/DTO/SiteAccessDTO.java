package com.software_project_team_15b.Ticketmaster.DTO;

/**
 * Read-only view of a user's current site-queue access state.
 */
public record SiteAccessDTO(
        boolean admitted,
        Integer position
) {
}
