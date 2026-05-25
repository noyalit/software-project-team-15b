package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;

/**
 * Domain service for managing per-event virtual queues.
 *
 * <p>Owns the per-event admission map, the persistent event-queue repository,
 * and the scheduler that advances event queues. Site-wide queue management
 * (site queue, admitted-token set, auth-dependent eviction) is the responsibility
 * of the application-layer {@code QueueService}, which holds the {@code IAuth}
 * dependency. The site-queue methods defined here ({@link #addUserToSiteQueue},
 * {@link #validateAndExitQueue}, {@link #canAccessWebsite}) are therefore not
 * supported by the standard implementation and will throw
 * {@link UnsupportedOperationException} if called directly.
 */
public interface IQueueDomainService {

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * @param token the user's auth token; must not be null
     * @param eventId     the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current access state
     */
    QueueAccessDTO requestAccess(String token, UUID eventId);

    /**
     * Returns {@code true} if the user identified by the given token currently holds
     * admitted access to the event.
     *
     * @param accessToken the user's auth token; must not be null
     * @param eventId     the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted to the event
     */
    boolean hasAccess(String accessToken, UUID eventId);

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors.
     *
     * @return {@code true} if the number of currently admitted users is below the site cap
     */
    boolean canAccessWebsite();

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     */
    void addUserToSiteQueue(String token);

    /**
     * Returns {@code true} if the given token has been admitted from the site queue and
     * may proceed to access the website.
     *
     * @param token the user's auth token; must not be null
     * @return {@code true} if the token is currently in the admitted set
     */
    boolean validateAndExitQueue(String token);

    /**
     * Returns a snapshot of the user's current access state for the given event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current state
     */
    QueueAccessDTO getQueueAccessView(String token, UUID eventId);

    /**
     * Returns the zero-based position of the given token in the event's waiting queue.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return the token's position (0 = next to be admitted)
     */
    int getPositionInEventQueue(String token, UUID eventId);

    /**
     * Returns {@code true} if {@code userId} is currently in the admitted window for {@code eventId},
     * without performing any token validation.
     *
     * @param token  the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     */
    boolean isUserAdmitted(String token, UUID eventId);

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    void createEventQueue(UUID eventId);

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    void deleteEventQueue(UUID eventId);

    /**
     * Removes and returns the user token at the front of the event's queue (FIFO order).
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the auth token of the user at the front of the queue
     */
    String popFromEventQueue(UUID eventId);

    /**
     * Appends the given user to the back of the event's queue.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the user's auth token; must not be null
     */
    void pushToEventQueue(UUID eventId, String token);
}
