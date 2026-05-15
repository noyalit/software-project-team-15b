package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import java.util.List;
import java.util.UUID;

public record HoldReceiptDTO(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        Money subtotal
) {
    public static HoldReceiptDTO from(HoldReceipt r) {
        return new HoldReceiptDTO(r.holdToken(), r.areaId(), r.seatIds(), r.quantity(), r.subtotal());
    }
}
