package com.software_project_team_15b.Ticketmaster.white.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.NotificationRepository;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.PresenceService;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.WebSocketNotifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * White-box unit tests for {@link WebSocketNotifier}, verifying the delivery and
 * persistence strategy for online versus offline recipients and resilience to
 * persistence failures.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PresenceService presenceService;

    private WebSocketNotifier notifier;

    private UUID userId;
    private NotificationDTO notification;

    @BeforeEach
    void setUp() {
        notifier = new WebSocketNotifier(messagingTemplate, notificationRepository, presenceService);
        userId = UUID.randomUUID();
        notification = new NotificationDTO(
                NotificationType.PURCHASE_SUCCESS,
                "Purchase complete",
                "Your tickets are confirmed.",
                Instant.now()
        );
    }

    @Test
    void notifyUserOnlinePushesLiveWithoutPersisting() {
        when(presenceService.isOnline(userId)).thenReturn(true);

        notifier.notifyUser(userId, notification);

        verify(messagingTemplate).convertAndSend("/topic/user/" + userId, notification);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyUserOfflinePersistsAsUnreadAndDoesNotPushLive() {
        when(presenceService.isOnline(userId)).thenReturn(false);

        notifier.notifyUser(userId, notification);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());

        NotificationEntity saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(NotificationType.PURCHASE_SUCCESS, saved.getType());
        assertEquals("Purchase complete", saved.getTitle());
        assertEquals("Your tickets are confirmed.", saved.getMessage());
        assertEquals(false, saved.isRead(), "stored notification must start unread so it is replayed on reconnect");

        // Offline: nothing is pushed live (it will be delivered on the next connection).
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void notifyUserOfflineSwallowsPersistenceFailure() {
        when(presenceService.isOnline(userId)).thenReturn(false);
        when(notificationRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> notifier.notifyUser(userId, notification));

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void notifyCompanyManagersBroadcastsToCompanyTopic() {
        UUID companyId = UUID.randomUUID();

        notifier.notifyCompanyManagers(companyId, notification);

        verify(messagingTemplate).convertAndSend("/topic/company/" + companyId + "/managers", notification);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyEventManagersBroadcastsToEventManagersTopic() {
        UUID eventId = UUID.randomUUID();

        notifier.notifyEventManagers(eventId, notification);

        verify(messagingTemplate).convertAndSend("/topic/event/" + eventId + "/managers", notification);
    }

    @Test
    void notifyEventAttendeesBroadcastsToEventAttendeesTopic() {
        UUID eventId = UUID.randomUUID();

        notifier.notifyEventAttendees(eventId, notification);

        verify(messagingTemplate).convertAndSend("/topic/event/" + eventId + "/attendees", notification);
    }

    @Test
    void groupBroadcastsDoNotConsultPresence() {
        UUID eventId = UUID.randomUUID();

        notifier.notifyEventAttendees(eventId, notification);

        verify(presenceService, never()).isOnline(eq(eventId));
    }
}
