package com.software_project_team_15b.Ticketmaster.DTO;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only snapshot of a single event's virtual queue, suitable for admin inspection.
 *
 * @param eventId       the event this queue belongs to
 * @param capacity      maximum number of users that may wait in the queue
 * @param maxAccepted   maximum number of users that may be simultaneously admitted
 * @param waitingCount  number of users currently waiting (not yet admitted)
 * @param admittedCount number of users currently holding active access
 * @param admittedUsers unmodifiable map of admitted token → access expiry time
 */
public record QueueSnapshotDTO(
        UUID eventId,
        int capacity,
        int maxAccepted,
        int waitingCount,
        int admittedCount,
        Map<String, LocalDateTime> admittedUsers
) {
    public QueueSnapshotDTO {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (admittedUsers == null) throw new IllegalArgumentException("admittedUsers cannot be null");
    }
}
