package com.software_project_team_15b.Ticketmaster.DTO;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueAccessDTO(
        UUID eventId,
        QueueAccessStatus status,
        Integer position,
        LocalDateTime accessExpiresAt
) {
    public QueueAccessDTO {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        if (status == QueueAccessStatus.WAITING && position == null) {
            throw new IllegalArgumentException("position is required when status is WAITING");
        }

        if (status != QueueAccessStatus.WAITING && position != null) {
            throw new IllegalArgumentException("position is only relevant when status is WAITING");
        }

        if (status == QueueAccessStatus.ADMITTED && accessExpiresAt == null) {
            throw new IllegalArgumentException("accessExpiresAt is required when status is ADMITTED");
        }

        if (status != QueueAccessStatus.ADMITTED && accessExpiresAt != null) {
            throw new IllegalArgumentException("accessExpiresAt is only relevant when status is ADMITTED");
        }
    }

    public boolean canCreateActiveOrder() {
        return status == QueueAccessStatus.NO_QUEUE
                || (status == QueueAccessStatus.ADMITTED
                    && LocalDateTime.now().isBefore(accessExpiresAt));
    }
}