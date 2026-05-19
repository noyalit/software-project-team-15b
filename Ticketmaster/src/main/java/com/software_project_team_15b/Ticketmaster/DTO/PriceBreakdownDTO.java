package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;

public record PriceBreakdownDTO(
        MoneyDTO basePrice,
        MoneyDTO subtotal,
        MoneyDTO discount,
        MoneyDTO total
) {
    public static PriceBreakdownDTO from(PriceBreakdown p) {
        return new PriceBreakdownDTO(MoneyDTO.from(p.basePrice()), MoneyDTO.from(p.subtotal()), MoneyDTO.from(p.discount()), MoneyDTO.from(p.total()));
    }
}
