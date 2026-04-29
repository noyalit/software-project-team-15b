package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
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

    public static ActiveOrderView from(ActiveOrder activeOrder, EventView eventView) {
        List<SeatInOrderView> seats = activeOrder.getOrderSeats().stream()
                .map(seatId -> {
                    EventView.AreaView area = eventView.areas().stream()
                            .filter(a -> a.seats().stream().anyMatch(s -> s.seatId().equals(seatId)))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Seat not found in event: " + seatId));

                    EventView.SeatView seatView = area.seats().stream()
                            .filter(s -> s.seatId().equals(seatId))
                            .findFirst()
                            .orElseThrow();

                    return new SeatInOrderView(
                            seatId,
                            seatView.row(),
                            seatView.number(),
                            seatView.status(),
                            area.areaId(),
                            area.name(),
                            area.basePrice()
                    );
                })
                .toList();

        return new ActiveOrderView(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                eventView.name(),
                eventView.artist(),
                eventView.startsAt(),
                eventView.location(),
                eventView.status(),
                activeOrder.getStatus(),
                activeOrder.getCreatedAt(),
                activeOrder.getExpiresAt(),
                seats
        );
    }    
}