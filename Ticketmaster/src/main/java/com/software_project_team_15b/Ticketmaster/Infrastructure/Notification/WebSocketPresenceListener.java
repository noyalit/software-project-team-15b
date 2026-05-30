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

/**
 * Bridges Spring's WebSocket session lifecycle events to the notification subsystem.
 *
 * <p>Two concerns are handled here:</p>
 * <ul>
 *   <li><b>Presence tracking</b> &ndash; when a session subscribes to a user's personal
 *       topic the user is marked online, and when the session disconnects it is
 *       unregistered (see {@link PresenceService}).</li>
 *   <li><b>Delayed delivery</b> &ndash; on subscription, any notifications that were
 *       persisted while the user was offline are replayed to them and marked as read.</li>
 * </ul>
 *
 * <p>This listener is the server-side counterpart of a client "registering after
 * login": it lets the system deliver messages that were produced while the recipient
 * was not connected.</p>
 */
@Component
public class WebSocketPresenceListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPresenceListener.class);

    private final PresenceService presenceService;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the listener with its collaborators.
     *
     * @param presenceService        tracks per-user connection state
     * @param notificationRepository source of persisted notifications to replay
     * @param messagingTemplate      used to push replayed notifications to the client
     */
    public WebSocketPresenceListener(PresenceService presenceService,
                                     NotificationRepository notificationRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles a client subscribing to a STOMP destination.
     *
     * <p>When the destination is a user's personal topic
     * ({@code /topic/user/{userId}}) the user is registered as online and any
     * notifications that were stored while they were offline are delivered (delayed
     * delivery). Subscriptions to other destinations, or with an unparseable user id,
     * are ignored.</p>
     *
     * <p>Each stored notification is delivered by first atomically
     * {@linkplain NotificationRepository#markAsReadIfUnread(UUID) claiming} it: only the
     * session that wins the claim sends it. This makes delivery exactly-once even when
     * the same user reconnects from several sessions at the same instant.</p>
     *
     * @param event the subscription event published by the messaging infrastructure
     */
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

                deliverPendingNotifications(userId);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Delivers every notification stored for the user while they were offline.
     *
     * <p>For each unread notification the read flag is flipped atomically via
     * {@link NotificationRepository#markAsReadIfUnread(UUID)}; the notification is only
     * pushed to the client if this caller won the claim ({@code 1} rows updated), which
     * guarantees no duplicate delivery across concurrently reconnecting sessions.</p>
     *
     * @param userId identifier of the user that just connected
     */
    private void deliverPendingNotifications(UUID userId) {
        List<NotificationEntity> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        for (NotificationEntity n : unread) {
            if (notificationRepository.markAsReadIfUnread(n.getId()) == 1) {
                NotificationDTO dto = new NotificationDTO(n.getType(), n.getTitle(), n.getMessage(), n.getCreatedAt());
                messagingTemplate.convertAndSend("/topic/user/" + userId, dto);
            }
        }
    }

    /**
     * Handles a client disconnecting, unregistering its session so that the affected
     * user is marked offline once they have no remaining connections.
     *
     * @param event the disconnect event published by the messaging infrastructure
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presenceService.unregisterSession(sessionId);
        LOG.debug("session {} disconnected and unregistered", sessionId);
    }
}
