package com.software_project_team_15b.Ticketmaster.Application.Order.Commands;

import java.util.Set;
import java.util.UUID;

public record AddSeatsToActiveOrderCommand(
        UUID orderId,
        UUID areaId,
        Integer standingQuantity,
        Set<UUID> seatIds
) {}

