package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands;

import java.util.Set;
import java.util.UUID;

public record CreateActiveOrderCommand(
        UUID eventId,
        UUID areaId,
        Integer standingQuantity,
        Set<UUID> seatIds
) {}
