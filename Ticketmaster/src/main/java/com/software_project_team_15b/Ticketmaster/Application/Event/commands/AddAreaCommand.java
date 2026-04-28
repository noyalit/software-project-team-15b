package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import java.util.List;

public record AddAreaCommand(
        String name,
        Money basePrice,
        AreaType type,
        Integer standingCapacity,
        List<SeatSpec> seats
) {
    public enum AreaType { SEATING, STANDING }

    public record SeatSpec(String row, String number) {}
}
