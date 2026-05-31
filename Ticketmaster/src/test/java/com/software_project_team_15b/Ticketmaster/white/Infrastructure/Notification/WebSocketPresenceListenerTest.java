package com.software_project_team_15b.Ticketmaster.white.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.NotificationRepository;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.PresenceService;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.WebSocketPresenceListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * White-box unit tests for {@link WebSocketPresenceListener}, covering presence
 * registration, replay of stored notifications on subscribe, and session cleanup on
 * disconnect.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketPresenceListenerTest {

    @Mock
    private PresenceService presenceService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketPresenceListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketPresenceListener(presenceService, notificationRepository, messagingTemplate);
    }

    @Test
    void subscribingToUserTopicRegistersPresenceAndReplaysClaimedNotifications() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-1";

        NotificationEntity stored = new NotificationEntity(
                userId,
                NotificationType.EVENT_CANCELLED,
                "Event cancelled",
                "The event you booked was cancelled.",
                Instant.now()
        );
        when(notificationRepository.findByUserIdAndReadFalse(userId)).thenReturn(List.of(stored));
        // This session wins the atomic claim, so it is responsible for delivering it.
        when(notificationRepository.markAsReadIfUnread(stored.getId())).thenReturn(1);

        listener.handleSessionSubscribe(subscribeEvent(sessionId, "/topic/user/" + userId));

        verify(presenceService).registerSubscription(sessionId, userId);
        verify(notificationRepository).markAsReadIfUnread(stored.getId());
        verify(messagingTemplate).convertAndSend(eq("/topic/user/" + userId), any(NotificationDTO.class));
    }

    @Test
    void subscribingDoesNotDeliverNotificationsClaimedByAnotherSession() {
        UUID userId = UUID.randomUUID();

        NotificationEntity stored = new NotificationEntity(
                userId,
                NotificationType.EVENT_CANCELLED,
                "Event cancelled",
                "The event you booked was cancelled.",
                Instant.now()
        );
        when(notificationRepository.findByUserIdAndReadFalse(userId)).thenReturn(List.of(stored));
        // Another concurrently-reconnecting session already claimed it: 0 rows updated.
        when(notificationRepository.markAsReadIfUnread(stored.getId())).thenReturn(0);

        listener.handleSessionSubscribe(subscribeEvent("session-1", "/topic/user/" + userId));

        verify(notificationRepository).markAsReadIfUnread(stored.getId());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void subscribingWithNoUnreadDoesNotClaimOrSend() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByUserIdAndReadFalse(userId)).thenReturn(List.of());

        listener.handleSessionSubscribe(subscribeEvent("session-1", "/topic/user/" + userId));

        verify(presenceService).registerSubscription("session-1", userId);
        verify(notificationRepository, never()).markAsReadIfUnread(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void subscribingToUnrelatedDestinationIsIgnored() {
        listener.handleSessionSubscribe(subscribeEvent("session-1", "/topic/event/" + UUID.randomUUID() + "/managers"));

        verifyNoInteractions(presenceService);
        verify(notificationRepository, never()).findByUserIdAndReadFalse(any());
    }

    @Test
    void subscribingWithMalformedUserIdIsIgnoredSafely() {
        listener.handleSessionSubscribe(subscribeEvent("session-1", "/topic/user/not-a-uuid"));

        verify(presenceService, never()).registerSubscription(any(), any());
        verify(notificationRepository, never()).findByUserIdAndReadFalse(any());
    }

    @Test
    void disconnectUnregistersSession() {
        listener.handleSessionDisconnect(disconnectEvent("session-1"));

        verify(presenceService).unregisterSession("session-1");
    }

    // --- helpers -------------------------------------------------------------

    private SessionSubscribeEvent subscribeEvent(String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }
}
