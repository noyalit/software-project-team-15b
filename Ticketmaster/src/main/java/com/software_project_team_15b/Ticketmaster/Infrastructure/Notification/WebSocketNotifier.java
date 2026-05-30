package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Component
public class WebSocketNotifier implements INotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void notifyUser(UUID userId, NotificationDTO notification) {
        messagingTemplate.convertAndSend("/topic/user/" + userId, notification);
    }

    @Override
    public void notifyCompanyManagers(UUID companyId, NotificationDTO notification) {
        messagingTemplate.convertAndSend("/topic/company/" + companyId + "/managers", notification);
    }

    @Override
    public void notifyEventManagers(UUID eventId, NotificationDTO notification) {
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/managers", notification);
    }

    @Override
    public void notifyEventAttendees(UUID eventId, NotificationDTO notification) {
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/attendees", notification);
    }

}
