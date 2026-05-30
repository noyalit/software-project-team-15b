package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.NotificationRepository;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Component
public class WebSocketNotifier implements INotifier {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.notification");

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate,
                             NotificationRepository notificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void notifyUser(UUID userId, NotificationDTO notification) {
        AUDIT.debug("op=notifyUser target=userId={} type={} title={}", userId, notification.getType(), notification.getTitle());
        try {
            NotificationEntity entity = new NotificationEntity(
                    userId,
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getCreatedAt()
            );
            notificationRepository.save(entity);
        } catch (RuntimeException e) {
            AUDIT.warn("op=persistNotification userId={} result=error reason={}", userId, e.getMessage(), e);
        }

        messagingTemplate.convertAndSend("/topic/user/" + userId, notification);
    }

    @Override
    public void notifyCompanyManagers(UUID companyId, NotificationDTO notification) {
        AUDIT.debug("op=notifyCompanyManagers target=companyId={} type={} title={}", companyId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/company/" + companyId + "/managers", notification);
    }

    @Override
    public void notifyEventManagers(UUID eventId, NotificationDTO notification) {
        AUDIT.debug("op=notifyEventManagers target=eventId={} type={} title={}", eventId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/managers", notification);
    }

    @Override
    public void notifyEventAttendees(UUID eventId, NotificationDTO notification) {
        AUDIT.debug("op=notifyEventAttendees target=eventId={} type={} title={}", eventId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/attendees", notification);
    }

}
