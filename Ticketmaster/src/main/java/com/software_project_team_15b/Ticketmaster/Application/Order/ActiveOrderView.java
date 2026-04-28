package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ActiveOrderView(
        UUID orderId,
        UUID userId,
        UUID eventId,
        String eventName,
        String artist,
        Instant startsAt,
        String location,
        EventStatus eventStatus,
        ActiveOrderStatus orderStatus,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<SeatInOrderView> seats
) {
    public record SeatInOrderView(
            UUID seatId,
            String row,
            String number,
            String seatStatus,
            UUID areaId,
            String areaName,
            Money basePrice
    ) {
    }
}