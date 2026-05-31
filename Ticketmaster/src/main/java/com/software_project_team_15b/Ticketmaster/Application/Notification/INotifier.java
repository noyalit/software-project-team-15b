package com.software_project_team_15b.Ticketmaster.Application.Notification;

import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

import java.util.UUID;

/**
 * Application-layer abstraction for sending notifications to users.
 *
 * <p>The application and domain layers decide <em>when</em> a notification should
 * be sent and express that intent through this interface. The concrete decision of
 * <em>how</em> a notification is delivered (WebSocket, email, persistence for offline
 * recipients, etc.) is left to an infrastructure implementation such as
 * {@code WebSocketNotifier}. This separation keeps the business logic free of any
 * transport or framework details.</p>
 *
 * <p>Implementations are expected to be best-effort and non-blocking from the caller's
 * point of view: a recipient who is currently offline should still eventually receive
 * the notification (for example by persisting it and replaying it on reconnect), and a
 * delivery failure must never propagate back into the calling business operation.</p>
 */
public interface INotifier {

        /**
         * Sends a notification to a single user.
         *
         * <p>If the user is currently connected the notification is delivered
         * immediately; otherwise the implementation is responsible for storing it so
         * it can be delivered once the user reconnects.</p>
         *
         * @param userId       the unique identifier of the recipient user; must not be {@code null}
         * @param notification the notification payload to deliver; must not be {@code null}
         */
        void notifyUser(UUID userId, NotificationDTO notification);

        /**
         * Sends a notification to every manager of the given company.
         *
         * @param companyId    the unique identifier of the company whose managers should be notified
         * @param notification the notification payload to deliver; must not be {@code null}
         */
        void notifyCompanyManagers(UUID companyId, NotificationDTO notification);

        /**
         * Sends a notification to every manager of the given event.
         *
         * @param eventId      the unique identifier of the event whose managers should be notified
         * @param notification the notification payload to deliver; must not be {@code null}
         */
        void notifyEventManagers(UUID eventId, NotificationDTO notification);

        /**
         * Sends a notification to every attendee (ticket holder) of the given event.
         *
         * @param eventId      the unique identifier of the event whose attendees should be notified
         * @param notification the notification payload to deliver; must not be {@code null}
         */
        void notifyEventAttendees(UUID eventId, NotificationDTO notification);

}
