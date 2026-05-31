package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationEntity}.
 *
 * <p>Persists notifications that could not be delivered live (offline recipients)
 * and supports retrieving them when the user reconnects. Standard CRUD operations
 * are inherited from {@link JpaRepository}.</p>
 */
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    /**
     * Returns all notifications for a user, newest first.
     *
     * @param userId identifier of the recipient user
     * @return the user's notifications ordered by creation time descending
     */
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Returns the user's notifications that have not yet been delivered/seen.
     *
     * @param userId identifier of the recipient user
     * @return the user's unread notifications
     */
    List<NotificationEntity> findByUserIdAndReadFalse(UUID userId);

    /**
     * Atomically marks a single notification as read, but only if it is still unread.
     *
     * <p>This is used to "claim" a stored (delayed) notification for delivery. The
     * conditional {@code read = false -> true} transition is performed in a single
     * database statement, so when several of a user's sessions reconnect at the same
     * time exactly one of them observes a return value of {@code 1} and is responsible
     * for delivering the notification; the others observe {@code 0} and skip it. This
     * prevents duplicate delivery without any application-level locking.</p>
     *
     * @param id identifier of the notification to claim
     * @return {@code 1} if this call transitioned the notification from unread to read,
     *         or {@code 0} if it was already read (claimed elsewhere) or does not exist
     */
    @Modifying
    @Transactional
    @Query("update NotificationEntity n set n.read = true where n.id = :id and n.read = false")
    int markAsReadIfUnread(@Param("id") UUID id);
}
