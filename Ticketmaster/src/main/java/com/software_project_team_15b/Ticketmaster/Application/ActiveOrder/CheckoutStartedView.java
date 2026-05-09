package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import java.time.LocalDateTime;
import java.util.UUID;

public record CheckoutStartedView(
        UUID orderId,
        UUID eventId,
        UUID areaId,
        LocalDateTime expiresAt
) {
}