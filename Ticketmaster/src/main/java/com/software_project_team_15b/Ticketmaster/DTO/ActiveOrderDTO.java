package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ActiveOrderDTO(
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
        MoneyDTO basePricePerSeat,
        MoneyDTO subtotal,
        MoneyDTO total,
        List<SeatInOrderDTO> seats
) {
    public record SeatInOrderDTO(
            UUID seatId,
            String row,
            String number,
            String seatStatus,
            MoneyDTO basePrice
    ) {
    }

    public static ActiveOrderDTO from(
            ActiveOrder activeOrder,
            EventDTO EventDTO,
            PriceBreakdown pricing
    ) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        if (EventDTO == null) {
            throw new IllegalArgumentException("EventDTO cannot be null");
        }
        if (pricing == null) {
            throw new IllegalArgumentException("PriceBreakdown cannot be null");
        }

        EventDTO.AreaView area = EventDTO.areas().stream()
                .filter(a -> a.areaId().equals(activeOrder.getAreaId()))
                .findFirst()
                .orElse(null);

        List<UUID> seatIds = activeOrder.getOrderSeats() == null
                ? List.of()
                : activeOrder.getOrderSeats().stream().toList();

        List<SeatInOrderDTO> seats = seatIds.stream()
                .map(seatId -> {
                    EventDTO.SeatView seatView = area == null
                            ? null
                            : area.seats().stream()
                                    .filter(s -> s.seatId().equals(seatId))
                                    .findFirst()
                                    .orElse(null);

                    return new SeatInOrderDTO(
                            seatId,
                            seatView == null ? null : seatView.row(),
                            seatView == null ? null : seatView.number(),
                            seatView == null ? null : seatView.status(),
                            MoneyDTO.from(pricing.basePrice())
                    );
                })
                .toList();

        int quantity = seatIds.size();

        return new ActiveOrderDTO(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                area == null ? "Unknown area" : area.name(),
                EventDTO.name(),
                EventDTO.artist(),
                EventDTO.startsAt(),
                EventDTO.location(),
                EventDTO.status(),
                activeOrder.getStatus(),
                activeOrder.getCreatedAt(),
                activeOrder.getExpiresAt(),
                quantity,
                MoneyDTO.from(pricing.basePrice()),
                MoneyDTO.from(pricing.basePrice().multiply(quantity)),
                MoneyDTO.from(pricing.total()),
                seats
        );
    }
}