package com.software_project_team_15b.Ticketmaster.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-layer facade over the queue domain.
 *
 * <p>Holds only the {@link IQueueDomainService} and forwards every call to it.
 * All queue state, repository access, scheduling, transactions and retry policy
 * live in the domain service; this class exists as the application-layer entry
 * point for queue operations so that callers in the application layer never need
 * to depend on another application service to use queue functionality.
 */
@Service
public class QueueService {

    private final IQueueDomainService queueDomainService;

    public QueueService(IQueueDomainService queueDomainService) {
        this.queueDomainService = Objects.requireNonNull(queueDomainService);
    }

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null or is already present in the queue
     */
    public void addUserToSiteQueue(String token) {
        queueDomainService.addUserToSiteQueue(token);
    }

    /**
     * Returns {@code true} if the given token has been admitted from the site queue and
     * may proceed to access the website.
     *
     * @param token the user's auth token; must not be null
     * @return {@code true} if the token is currently in the admitted set
     * @throws IllegalArgumentException if {@code token} is null
     */
    public boolean validateAndExitQueue(String token) {
        return queueDomainService.validateAndExitQueue(token);
    }

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors.
     *
     * @return {@code true} if the number of currently admitted users is below the site cap
     */
    public boolean canAccessWebsite() {
        return queueDomainService.canAccessWebsite();
    }

    /**
     * Returns the zero-based position of the given token in the event's waiting queue.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return the token's position (0 = next to be admitted)
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null,
     *                                  or if the token is not present in the queue
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public int getPositionInEventQueue(String token, UUID eventId) {
        return queueDomainService.getPositionInEventQueue(token, eventId);
    }

    /**
     * Returns {@code true} if {@code userId} is currently in the admitted window for {@code eventId},
     * without performing any token validation.
     *
     * @param userId  the user to check; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     */
    public boolean isUserAdmitted(UUID userId, UUID eventId) {
        return queueDomainService.isUserAdmitted(userId, eventId);
    }

    /**
     * Returns a snapshot of the user's current access state for the given event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if a queue exists for the event but cannot be read
     */
    public QueueAccessDTO getQueueAccessView(String token, UUID eventId) {
        return queueDomainService.getQueueAccessView(token, eventId);
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current access state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws QueueIsFullException     if the persistent queue has reached its capacity
     * @throws AlreadyInQueueException  if the user is already waiting in the queue
     */
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        return queueDomainService.requestAccess(token, eventId);
    }

    /**
     * Returns {@code true} if the user identified by the given token currently holds
     * admitted access to the event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted to the event
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     */
    public boolean hasAccess(String token, UUID eventId) {
        return queueDomainService.hasAccess(token, eventId);
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    public void createEventQueue(UUID eventId) {
        queueDomainService.createEventQueue(eventId);
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void deleteEventQueue(UUID eventId) {
        queueDomainService.deleteEventQueue(eventId);
    }

    /**
     * Removes and returns the user token at the front of the event's queue (FIFO order).
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the auth token of the user at the front of the queue
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws EmptyQueueException      if the queue contains no entries
     */
    public String popFromEventQueue(UUID eventId) {
        return queueDomainService.popFromEventQueue(eventId);
    }

    /**
     * Appends the given user to the back of the event's queue.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the user's auth token; must not be null
     * @throws IllegalArgumentException  if {@code eventId} or {@code token} is null
     * @throws QueueNotFoundException    if no queue exists for the given event
     * @throws QueueIsFullException      if the queue has reached its capacity
     * @throws AlreadyInQueueException   if the token is already waiting in the queue
     */
    public void pushToEventQueue(UUID eventId, String token) {
        queueDomainService.pushToEventQueue(eventId, token);
    }
}
