package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.Set;
import java.util.UUID;

public record TicketIssueRequestDTO(
        UUID customerId,
        UUID eventId,
        UUID areaId,
        boolean seating,
        Set<UUID> seatIds,
        int quantity
) {
}