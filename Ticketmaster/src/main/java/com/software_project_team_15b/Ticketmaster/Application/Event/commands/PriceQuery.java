package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import java.time.LocalDate;
import java.util.UUID;

public record PriceQuery(
        UUID areaId,
        int quantity,
        UUID buyerId,
        LocalDate buyerBirthDate,
        String couponCode
) {
    public PriceQuery {
        if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");
    }
}
