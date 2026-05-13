package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueAccessView;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
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
    public QueueAccessView requestAccess(String accessToken, UUID eventId) {
        return queueService.requestAccess(accessToken, eventId);
    }

    @Override
    public boolean hasAccess(String accessToken, UUID eventId) {
        return queueService.hasAccess(accessToken, eventId);
    }
}