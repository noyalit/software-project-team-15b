package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;

public record EventAvailabilityDTO(EventAvailability status) {
    public static EventAvailabilityDTO from(EventAvailability status) {
        return new EventAvailabilityDTO(status);
    }
}
