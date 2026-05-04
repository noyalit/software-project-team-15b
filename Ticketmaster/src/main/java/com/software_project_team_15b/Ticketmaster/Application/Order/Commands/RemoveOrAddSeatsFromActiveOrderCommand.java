package com.software_project_team_15b.Ticketmaster.Application.Order.Commands;

import java.util.Set;
import java.util.UUID;

public record RemoveOrAddSeatsFromActiveOrderCommand(
        UUID orderId,
        Set<UUID> seatIds
) {
}
