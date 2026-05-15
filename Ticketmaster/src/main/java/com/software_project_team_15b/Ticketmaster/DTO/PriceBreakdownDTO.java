package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;

public record PriceBreakdownDTO(
        Money basePrice,
        Money subtotal,
        Money discount,
        Money total
) {
    public static PriceBreakdownDTO from(PriceBreakdown p) {
        return new PriceBreakdownDTO(p.basePrice(), p.subtotal(), p.discount(), p.total());
    }
}
