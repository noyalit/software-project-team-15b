package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;



import java.time.Instant;

/**
 * Immutable data-transfer object describing a single notification.
 *
 * <p>This is the payload exchanged between the application layer (which produces
 * notifications) and the infrastructure layer (which delivers them), and it is also
 * the shape serialized to clients over WebSocket. It intentionally carries only the
 * presentation-relevant fields and no persistence or routing concerns.</p>
 */
    public class NotificationDTO {

        /** The category of the notification, used by clients to render/route it. */
        private NotificationType type;

        /** Short human-readable headline for the notification. */
        private String title;

        /** Full human-readable body of the notification. */
        private String message;

        /** The instant at which the notification was created. */
        private Instant createdAt;

        /**
         * Creates a notification payload.
         *
         * @param type      the category of the notification
         * @param title     short human-readable headline
         * @param message   full human-readable body
         * @param createdAt the instant the notification was created
         */
        public NotificationDTO(NotificationType type, String title, String message, Instant createdAt) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
        }


        /**
         * @return the category of the notification
         */
        public NotificationType getType() {
            return type;
        }

        /**
         * @return the short human-readable headline
         */
        public String getTitle() {
            return title;
        }

        /**
         * @return the full human-readable body
         */
        public String getMessage() {
            return message;
        }

        /**
         * @return the instant the notification was created
         */
        public Instant getCreatedAt() {
            return createdAt;
        }

    }
