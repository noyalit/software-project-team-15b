package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueueDomainServiceImpl implements IQueueDomainService {

    private final QueueService queueService;
    private final IAuth auth;

    public QueueDomainServiceImpl(QueueService queueService, IAuth auth) {
        this.queueService = Objects.requireNonNull(queueService);
        this.auth = Objects.requireNonNull(auth);
    }

    @Override
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        UUID userId = auth.extractUserId(token);
        if (!queueService.isUserAdmitted(userId, eventId)) {
            queueService.pushToEventQueue(eventId, token);
        }
        return queueService.getQueueAccessView(token, eventId);
    }

    @Override
    public boolean hasAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        UUID userId = auth.extractUserId(token);
        return queueService.isUserAdmitted(userId, eventId);
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
