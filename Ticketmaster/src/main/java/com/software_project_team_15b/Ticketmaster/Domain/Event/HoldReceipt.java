package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.util.List;
import java.util.UUID;

public record HoldReceipt(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        Money subtotal
) {}
