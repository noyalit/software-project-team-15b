package com.software_project_team_15b.Ticketmaster.DTO;

/**
 * Read-only snapshot of the site-wide queue state, suitable for admin inspection.
 */
public record SiteQueueSnapshotDTO(
        int maxVisitors,
        int waitingCount,
        int admittedCount
) {
}
