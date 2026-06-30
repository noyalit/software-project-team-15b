package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SiteQueueSnapshotDTO;

/**
 * Domain service for managing per-event virtual queues and the site-wide waiting queue.
 *
 * <p>Owns the per-event admission map, the in-memory event-queue repository, the
 * scheduler that advances event queues, and the in-memory site queue together with its
 * admitted-token set. All queue state is held in memory only and is never persisted.
 * Auth-dependent eviction (checking token validity before admitting
 * a user) is the responsibility of the application-layer {@code QueueService}, which
 * holds the {@code IAuth} dependency and calls {@link #removeAcceptedToken} and
 * {@link #acceptUsersFromSiteQueue} on a schedule.
 */
public interface IQueueDomainService {

    /**
     * Returns an unmodifiable snapshot of the tokens that are currently admitted to
     * the site (i.e. past the site queue and within their access window).
     *
     * @return an unmodifiable view of the admitted-token set
     */
    Set<String> getAcceptedTokens();

    /**
     * Drains the front of the site queue into the admitted set until the maximum
     * concurrent-visitor cap is reached.
     */
    void acceptUsersFromSiteQueue();

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null and must not already be waiting
     * @throws IllegalArgumentException if {@code token} is null or already in the queue
     */
    void addUserToSiteQueue(String token);

    /**
     * Atomically attempts to admit the given token to the site, respecting the current
     * max-visitors cap.
     *
     * @param token the user's auth token; must not be null
     * @return {@code true} if admitted; {@code false} if the site is at capacity
     */
    boolean tryAdmitToSite(String token);

    /**
     * Replaces a token in the site tracking structures (admitted set / waiting queue)
     * while preserving the user's place/state. Used when exchanging tokens (e.g.
     * guest/temp token -> member token).
     *
     * @param oldToken previous token; must not be null
     * @param newToken new token; must not be null
     */
    void replaceSiteToken(String oldToken, String newToken);

    /**
     * Removes the given token from both the admitted set and the site waiting queue,
     * if present.
     *
     * @param token token to evict; must not be null
     */
    void evictSiteToken(String token);

    /**
     * @param token user's auth token
     * @return true if the token is currently in the admitted set for the site queue
     */
    boolean isSiteTokenAccepted(String token);

    /**
     * Returns the zero-based position of the given token in the site waiting queue.
     *
     * @param token user's auth token
     * @return 0-based position, or {@code -1} if the token is not currently waiting
     */
    int getSiteQueuePosition(String token);

    /**
     * Marks the given token as an admitted (active) site visitor by adding it directly
     * to the admitted-token set. Used when a visitor enters while the site still has
     * capacity, so the visitor cap reflects active visitors.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null
     */
    void admitToken(String token);

    /**
     * Returns a snapshot of the site-wide queue state.
     */
    SiteQueueSnapshotDTO getSiteQueueSnapshot();

    /**
     * Updates the maximum number of concurrently admitted visitors.
     */
    void updateSiteQueueSettings(int maxVisitors);

    /**
     * Removes the given token from the admitted set.
     *
     * @param token the user's auth token to remove; must not be null
     * @throws IllegalArgumentException if {@code token} is null
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException if {@code token} is not present in the admitted set
     */
    void removeAcceptedToken(String token);

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
     * Removes admitted access for the given token (if present) and immediately advances
     * the event queue to fill any newly freed slot.
     *
     * <p>Used when a user no longer needs the temporary admitted access window (e.g.
     * after successfully creating an active order), so the next waiting user can be
     * admitted without waiting for access expiry.
     */
    void releaseEventAccess(String token, UUID eventId);

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
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId      the unique identifier of the event; must not be null
     * @param capacity     the maximum number of users that may wait in the queue; must be non-negative
     * @param max_accepted the maximum number of users that may be simultaneously admitted; must be non-negative
     * @throws IllegalArgumentException if {@code eventId} is null, or either capacity value is negative
     */
    void createEventQueue(UUID eventId, int capacity, int max_accepted);

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

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors.
     *
     * @return {@code true} if the number of currently admitted users is below the site cap
     */
    boolean canAccessWebsite();

    /**
     * Removes the given user from both the waiting list and the admitted set for the
     * given event queue. A no-op if the user is not present in either.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws QueueNotFoundException if no queue exists for the given event
     */
    void clearEventQueue(UUID eventId);

    /**
     * Returns a snapshot of the virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueSnapshotDTO} describing the queue's current state
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException if no queue exists for the given event
     */
    QueueSnapshotDTO getQueueSnapshot(UUID eventId);

    /**
     * Returns snapshots of all virtual queues currently in the repository.
     *
     * @return an unmodifiable list of {@link QueueSnapshotDTO}, one per in-memory queue
     */
    List<QueueSnapshotDTO> getAllQueueSnapshots();

    /**
     * Updates the capacity and max-accepted limits of the virtual queue for the given event.
     *
     * @param eventId      the unique identifier of the event; must not be null
     * @param capacity     the new maximum number of users that may wait; must be non-negative
     * @param max_accepted the new maximum number of simultaneously admitted users; must be non-negative
     * @throws IllegalArgumentException if {@code eventId} is null or either limit is negative
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException if no queue exists for the given event
     */
    void updateQueueSettings(UUID eventId, int capacity, int max_accepted);
}
