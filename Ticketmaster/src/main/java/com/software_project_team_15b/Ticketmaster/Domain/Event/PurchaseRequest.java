package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequest(
        UUID eventId,
        UUID areaId,
        UUID buyerId,
        LocalDate buyerBirthDate,
        int quantity,
        List<UUID> seatIds,
        String couponCode
) {
    public PurchaseRequest {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
    }
}
