package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;

import java.util.List;
import java.util.UUID;

public record HoldReceiptDTO(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        MoneyDTO subtotal
) {
    public static HoldReceiptDTO from(HoldReceipt r) {
        return new HoldReceiptDTO(r.holdToken(), r.areaId(), r.seatIds(), r.quantity(), MoneyDTO.from(r.subtotal()));
    }
}
