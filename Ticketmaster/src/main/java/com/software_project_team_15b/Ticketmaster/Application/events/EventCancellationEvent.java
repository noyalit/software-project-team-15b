package com.software_project_team_15b.Ticketmaster.Application.events;

import java.util.UUID;

public record EventCancellationEvent(UUID eventId, UUID cancelledById) {
}