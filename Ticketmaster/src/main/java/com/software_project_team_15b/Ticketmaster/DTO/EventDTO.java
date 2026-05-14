package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDTO(
        UUID eventId,
        UUID companyId,
        String name,
        String artist,
        Category category,
        Instant startsAt,
        String location,
        EventStatus status,
        List<AreaView> areas
) {
    public record AreaView(
            UUID areaId,
            String name,
            Money basePrice,
            String type,
            int availableCapacity,
            List<SeatView> seats
    ) {}

    public record SeatView(UUID seatId, String row, String number, String status) {}

    public static EventDTO from(Event e) {
        List<AreaView> areas = e.areas().stream().map(EventDTO::toAreaView).toList();
        return new EventDTO(
                e.eventId(), e.companyId(), e.name(), e.artist(), e.category(),
                e.startsAt(), e.location(), e.status(), areas
        );
    }

    private static AreaView toAreaView(EventArea a) {
        String type = a instanceof SeatingEventArea ? "SEATING"
                : a instanceof StandingEventArea ? "STANDING"
                : "UNKNOWN";
        List<SeatView> seats = a instanceof SeatingEventArea s
                ? s.seats().values().stream()
                        .map(seat -> new SeatView(seat.seatId(), seat.row(), seat.number(), seat.status().name()))
                        .toList()
                : List.of();
        return new AreaView(a.areaId(), a.name(), a.basePrice(), type, a.availableCapacity(), seats);
    }
}
