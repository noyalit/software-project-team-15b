package com.software_project_team_15b.Ticketmaster.Domain.Event;

public record PriceBreakdown(
        Money basePrice,
        Money subtotal,
        Money discount,
        Money total
) {}
