package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Infrastructure implementation of {@link INotifier} that delivers notifications over
 * STOMP-over-WebSocket and persists them for offline recipients.
 *
 * <p>Delivery strategy for a single user:</p>
 * <ul>
 *   <li>If the user is {@link PresenceService#isOnline(UUID) online}, the notification
 *       is pushed live to their personal topic and is <em>not</em> persisted (avoids
 *       unnecessary database writes).</li>
 *   <li>If the user is offline, the notification is stored via
 *       {@link NotificationRepository} and <em>not</em> pushed (there is no subscriber).
 *       It is replayed the next time the user connects (delayed delivery), handled by
 *       {@code WebSocketPresenceListener}.</li>
 * </ul>
 *
 * <p>Group notifications (company managers, event managers, event attendees) are
 * fan-out broadcasts to a shared topic and are delivered live only; offline members
 * pick them up through their own per-user channel when the producing logic also
 * targets individuals.</p>
 *
 * <p>This class never throws back into the caller: a persistence failure is logged to
 * the audit log and swallowed so that a notification problem cannot abort the business
 * operation that triggered it.</p>
 */
@Component
public class WebSocketNotifier implements INotifier {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.notification");

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final PresenceService presenceService;

    /**
     * Creates the notifier with its collaborators.
     *
     * @param messagingTemplate      Spring template used to publish messages to STOMP topics
     * @param notificationRepository repository used to persist notifications for offline users
     * @param presenceService        tracks which users are currently connected
     */
    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate,
                             NotificationRepository notificationRepository,
                             PresenceService presenceService) {
        this.messagingTemplate = messagingTemplate;
        this.notificationRepository = notificationRepository;
        this.presenceService = presenceService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pushes live if the user is online; otherwise persists the notification so it
     * can be delivered the next time the user connects (delayed delivery). Persistence
     * failures are logged and never propagated to the caller.</p>
     */
    @Override
    public void notifyUser(UUID userId, NotificationDTO notification) {
        AUDIT.debug("op=notifyUser target=userId={} type={} title={}", userId, notification.getType(), notification.getTitle());
        // If user is online, send without persisting to reduce DB writes.
        if (presenceService.isOnline(userId)) {
            messagingTemplate.convertAndSend("/topic/user/" + userId, notification);
            return;
        }

        // User is offline: store the notification for delayed delivery. There is no
        // active subscriber, so we do not push it now; it will be replayed on the
        // user's next connection by WebSocketPresenceListener.
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
    }

    /**
     * {@inheritDoc}
     *
     * <p>Broadcasts live to the company's managers topic.</p>
     */
    @Override
    public void notifyCompanyManagers(UUID companyId, NotificationDTO notification) {
        AUDIT.debug("op=notifyCompanyManagers target=companyId={} type={} title={}", companyId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/company/" + companyId + "/managers", notification);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Broadcasts live to the event's managers topic.</p>
     */
    @Override
    public void notifyEventManagers(UUID eventId, NotificationDTO notification) {
        AUDIT.debug("op=notifyEventManagers target=eventId={} type={} title={}", eventId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/managers", notification);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Broadcasts live to the event's attendees topic.</p>
     */
    @Override
    public void notifyEventAttendees(UUID eventId, NotificationDTO notification) {
        AUDIT.debug("op=notifyEventAttendees target=eventId={} type={} title={}", eventId, notification.getType(), notification.getTitle());
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/attendees", notification);
    }

}
