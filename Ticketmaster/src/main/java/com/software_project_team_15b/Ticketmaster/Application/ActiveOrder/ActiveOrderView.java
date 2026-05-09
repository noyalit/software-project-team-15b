package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ActiveOrderView(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID areaId,
        String areaName,
        String eventName,
        String artist,
        Instant startsAt,
        String location,
        EventStatus eventStatus,
        ActiveOrderStatus orderStatus,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        int quantity,
        Money basePricePerSeat,
        Money subtotal,
        Money total,
        List<SeatInOrderView> seats
) {
    public record SeatInOrderView(
            UUID seatId,
            String row,
            String number,
            String seatStatus,
            Money basePrice
    ) {
    }

    public static ActiveOrderView from(
            ActiveOrder activeOrder,
            EventView eventView,
            PriceBreakdown pricing
    ) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        if (eventView == null) {
            throw new IllegalArgumentException("EventView cannot be null");
        }
        if (pricing == null) {
            throw new IllegalArgumentException("PriceBreakdown cannot be null");
        }

        EventView.AreaView area = eventView.areas().stream()
                .filter(a -> a.areaId().equals(activeOrder.getAreaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Area not found in event: " + activeOrder.getAreaId()
                ));

        List<SeatInOrderView> seats = activeOrder.getOrderSeats().stream()
                .map(seatId -> {
                    EventView.SeatView seatView = area.seats().stream()
                            .filter(s -> s.seatId().equals(seatId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Seat not found in area: " + seatId
                            ));

                    return new SeatInOrderView(
                            seatId,
                            seatView.row(),
                            seatView.number(),
                            seatView.status(),
                            pricing.basePrice()
                    );
                })
                .toList();

        int quantity = activeOrder.getOrderSeats().size();

        return new ActiveOrderView(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                area.name(),
                eventView.name(),
                eventView.artist(),
                eventView.startsAt(),
                eventView.location(),
                eventView.status(),
                activeOrder.getStatus(),
                activeOrder.getCreatedAt(),
                activeOrder.getExpiresAt(),
                quantity,
                pricing.basePrice(),
                pricing.basePrice().multiply(quantity),
                pricing.total(),
                seats
        );
    }
}