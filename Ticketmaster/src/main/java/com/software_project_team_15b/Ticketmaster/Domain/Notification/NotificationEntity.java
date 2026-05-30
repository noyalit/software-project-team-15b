package com.software_project_team_15b.Ticketmaster.Domain.Notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;

    @Column(length = 2000)
    private String message;

    private Instant createdAt;

    private boolean read = false;

    protected NotificationEntity() {}

    public NotificationEntity(UUID userId,
                              NotificationType type,
                              String title,
                              String message,
                              Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void markAsRead() {
        this.read = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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

    public boolean isRead() {
        return read;
    }
}