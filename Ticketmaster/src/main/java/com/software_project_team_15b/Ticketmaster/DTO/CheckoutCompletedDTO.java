package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

public record CheckoutCompletedDTO(
        UUID orderId,
        UUID eventId,
        UUID areaId,
        int ticketCount,
        MoneyDTO totalPrice
) {
}