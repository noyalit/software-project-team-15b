package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.UUID;

@Component
public class WebSocketPresenceListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPresenceListener.class);

    private final PresenceService presenceService;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPresenceListener(PresenceService presenceService,
                                     NotificationRepository notificationRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) return;

        String dest = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        if (dest != null && dest.startsWith("/topic/user/")) {
            String userPart = dest.substring("/topic/user/".length());
            try {
                UUID userId = UUID.fromString(userPart);
                presenceService.registerSubscription(sessionId, userId);
                LOG.debug("session {} subscribed to user {}", sessionId, userId);

                // deliver unread notifications
                List<NotificationEntity> unread = notificationRepository.findByUserIdAndReadFalse(userId);
                if (!unread.isEmpty()) {
                    for (NotificationEntity n : unread) {
                        NotificationDTO dto = new NotificationDTO(n.getType(), n.getTitle(), n.getMessage(), n.getCreatedAt());
                        messagingTemplate.convertAndSend("/topic/user/" + userId, dto);
                        n.markAsRead();
                    }
                    notificationRepository.saveAll(unread);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presenceService.unregisterSession(sessionId);
        LOG.debug("session {} disconnected and unregistered", sessionId);
    }
}
