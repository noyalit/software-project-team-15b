package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationEntity> findByUserIdAndReadFalse(UUID userId);
}
