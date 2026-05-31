package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;

import java.util.List;
import java.util.UUID;

public record EventCancelResult(EventDTO snapshot, List<UUID> attendeeIds) {

}
