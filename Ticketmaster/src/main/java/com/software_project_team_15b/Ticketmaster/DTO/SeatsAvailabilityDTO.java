package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SeatsAvailabilityDTO(Set<UUID> available, Set<UUID> unavailable) {
    public static SeatsAvailabilityDTO from(Map<Boolean, Set<UUID>> map) {
        return new SeatsAvailabilityDTO(
                map.getOrDefault(Boolean.TRUE, Set.of()),
                map.getOrDefault(Boolean.FALSE, Set.of())
        );
    }
}
