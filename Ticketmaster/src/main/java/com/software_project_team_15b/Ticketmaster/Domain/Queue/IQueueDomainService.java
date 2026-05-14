package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;

public interface IQueueDomainService {
    QueueAccessDTO requestAccess(String accessToken, UUID eventId);

    boolean hasAccess(String accessToken, UUID eventId);
}
