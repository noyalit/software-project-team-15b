package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;



import java.time.Instant;

    public class NotificationDTO {

        private NotificationType type;

        private String title;

        private String message;

        private Instant createdAt;

        public NotificationDTO(NotificationType type, String title, String message, Instant createdAt) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
        }


        public NotificationType getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

    }
