package com.software_project_team_15b.Ticketmaster.DTO;

import java.time.LocalDateTime;
import java.util.UUID;

public record CheckoutStartedDTO(
        UUID orderId,
        UUID eventId,
        UUID areaId,
        LocalDateTime expiresAt
) {
}