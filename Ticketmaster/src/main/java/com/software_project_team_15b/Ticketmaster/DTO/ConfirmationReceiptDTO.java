package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;

import java.util.List;
import java.util.UUID;

public record ConfirmationReceiptDTO(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        MoneyDTO total
) {
    public static ConfirmationReceiptDTO from(ConfirmationReceipt r) {
        return new ConfirmationReceiptDTO(r.holdToken(), r.areaId(), r.seatIds(), r.quantity(), MoneyDTO.from(r.total()));
    }
}
