package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueueDomainServiceImpl implements IQueueDomainService {

    private final QueueService queueService;

    public QueueDomainServiceImpl(QueueService queueService) {
        this.queueService = Objects.requireNonNull(queueService);
    }

    @Override
    public QueueAccessDTO requestAccess(String accessToken, UUID eventId) {
        return queueService.requestAccess(accessToken, eventId);
    }

    @Override
    public boolean hasAccess(String accessToken, UUID eventId) {
        return queueService.hasAccess(accessToken, eventId);
    }

    @Override
    public boolean canAccessWebsite() {
        return queueService.canAccessWebsite();
    }

    @Override
    public void addUserToSiteQueue(String token) {
        queueService.addUserToSiteQueue(token);
    }

    @Override
    public boolean validateAndExitQueue(String token) {
        return queueService.validateAndExitQueue(token);
    }
}