package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Domain service for managing virtual queues associated with events and the site-wide
 * waiting queue.
 *
 * <p>Owns the persistent {@link IQueueRepository} aggregate, the per-event admission
 * map, the in-memory site queue ({@link #siteQueue}), the admitted-token set
 * ({@link #acceptedTokens}), and the scheduler that drains event queues and expires
 * per-event access windows. Each event may have at most one queue, keyed by the event's UUID.
 *
 * <p>Auth-dependent eviction (validating token freshness before admitting users from the
 * site queue) is handled by the application-layer {@code QueueService}, which calls
 * {@link #removeAcceptedToken} and {@link #acceptUsersFromSiteQueue} on a schedule.
 *
 * <p>Mutating operations on persistent queues are transactional. Methods that perform a
 * read-then-write are additionally annotated with {@link Retryable} so that transient
 * optimistic-lock conflicts are transparently retried before propagating an error to the caller.
 */
@Service
public class QueueDomainServiceImpl implements IQueueDomainService {

    private final int ACCESS_TIME = 100;
    private static final int MAX_VISITORS = 100;

    private final IQueueRepository queueRepository;
    // Self-reference through the Spring proxy so that @Retryable and @Transactional
    // on popFromEventQueue take effect when called from advanceEventQueue.
    @Autowired @Lazy private IQueueDomainService self;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, LocalDateTime>> eventAccess = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Queue<String> siteQueue = new LinkedList<>();
    private final Set<String> acceptedTokens = new HashSet<>();

    public QueueDomainServiceImpl(IQueueRepository queueRepository) {
        this.queueRepository = Objects.requireNonNull(queueRepository);
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
    @Override
    public int getPositionInEventQueue(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue with id " + eventId + " not found");
        }
        return queue.getPosition(token);
    }

    /**
     * Returns an unmodifiable snapshot of the tokens that are currently admitted to
     * the site (i.e. past the site queue and within their access window).
     *
     * @return an unmodifiable view of the admitted-token set
     */
    public Set<String> getAcceptedTokens() {
        return Collections.unmodifiableSet(acceptedTokens);
    }

    /**
     * Removes the given token from the admitted set.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException if {@code token} is not present in the admitted set
     */
    public void removeAcceptedToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (!acceptedTokens.contains(token)) {
            throw new InvalidTokenException("token " + token + " not found in accepted tokens");
        }
        acceptedTokens.remove(token);
    }

    /**
     * Drains the front of the site queue into the admitted set until
     * {@link #MAX_VISITORS} concurrent visitors are admitted or the queue is empty.
     */
    public synchronized void acceptUsersFromSiteQueue() {
        while (!siteQueue.isEmpty() && acceptedTokens.size() < MAX_VISITORS) {
            acceptedTokens.add(siteQueue.poll());
        }
    }

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null or is already present in the queue
     */
    public synchronized void addUserToSiteQueue(String token) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (siteQueue.contains(token)) {
            throw new IllegalArgumentException("User is already in the site queue");
        }
        siteQueue.add(token);
    }

    /**
     * Removes a user's timed access window for an event and immediately tries to fill
     * the vacated slot from the waiting queue.
     *
     * <p>Called automatically by the scheduler when a user's {@link #ACCESS_TIME}-second
     * window expires. If the event has been deleted (no access map present) the call is a no-op.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     */
    protected synchronized void clearEventAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<String, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        access.remove(token);
        advanceEventQueue(eventId);
    }

    /**
     * Fills available admission slots for the given event by promoting users from its
     * persistent waiting queue.
     *
     * <p>Up to 100 users may hold simultaneous access to an event. Each promoted user is
     * granted a {@link #ACCESS_TIME}-second window; a callback is registered with the
     * scheduler to call {@link #clearEventAccess(String, UUID)} when that window closes.
     * Stops early if the queue is empty.
     *
     * <p>Uses {@code self} (the Spring proxy) to invoke {@link #popFromEventQueue} so
     * that {@link org.springframework.retry.annotation.Retryable} and
     * {@link org.springframework.transaction.annotation.Transactional} take effect.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    protected synchronized void advanceEventQueue(UUID eventId) {
        ConcurrentHashMap<String, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        while (access.size() < 100) {
            try {
                String nextToken = self.popFromEventQueue(eventId);
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ACCESS_TIME);
                access.put(nextToken, expiresAt);
                scheduler.schedule(() -> clearEventAccess(nextToken, eventId), ACCESS_TIME, TimeUnit.SECONDS);
            } catch (EmptyQueueException e) {
                break;
            }
        }
    }

    /**
     * Returns {@code true} if {@code token} is currently in the admitted window for {@code eventId}.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     */
    @Override
    public boolean isUserAdmitted(String token, UUID eventId) {
        ConcurrentHashMap<String, LocalDateTime> access = eventAccess.get(eventId);
        return access != null && access.containsKey(token);
    }

    /**
     * Returns a snapshot of the user's current access state for the given event.
     *
     * <ul>
     *   <li>{@link QueueAccessStatus#NO_QUEUE} — the event has no virtual queue; the user
     *       may proceed directly.</li>
     *   <li>{@link QueueAccessStatus#ADMITTED} — the user has been admitted; the view
     *       includes the exact time at which access expires.</li>
     *   <li>{@link QueueAccessStatus#WAITING} — the user is still in the queue; the view
     *       includes their zero-based position.</li>
     * </ul>
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null,
     *                                  or if the user is not present in the queue (when WAITING)
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if a queue exists for the event but cannot be read
     */
    @Override
    public QueueAccessDTO getQueueAccessView(String token, UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<String, LocalDateTime> admittedUsers = eventAccess.get(eventId);
        if (admittedUsers == null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.NO_QUEUE, null, null);
        }
        LocalDateTime expiresAt = admittedUsers.get(token);
        if (expiresAt != null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.ADMITTED, null, expiresAt);
        }
        int position = getPositionInEventQueue(token, eventId);
        return new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, position, null);
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * <p>If the user is already admitted their current {@link QueueAccessDTO} is returned
     * immediately without re-queuing them.
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
    @Override
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<String, LocalDateTime> admittedUsers = eventAccess.get(eventId);
        if (admittedUsers == null || !admittedUsers.containsKey(token)) {
            self.pushToEventQueue(eventId, token);
        }
        return getQueueAccessView(token, eventId);
    }

    /**
     * Returns {@code true} if the user identified by the given token currently holds
     * admitted access to the event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted to the event
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     */
    @Override
    public boolean hasAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        return isUserAdmitted(token, eventId);
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    @Override
    @Transactional
    public void createEventQueue(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = new VirtualQueue(eventId);
        queueRepository.addQueue(queue);
        eventAccess.put(eventId, new ConcurrentHashMap<>());
        advanceEventQueue(eventId);
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Override
    @Transactional
    public void deleteEventQueue(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        queueRepository.removeQueue(queue);
        eventAccess.remove(eventId);
    }

    /**
     * Removes and returns the user token at the front of the event's queue (FIFO order).
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the auth token of the user at the front of the queue
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws EmptyQueueException      if the queue contains no entries
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public String popFromEventQueue(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        if (queue.isEmpty()) {
            throw new EmptyQueueException("Event queue is empty (eventId: " + eventId + ")");
        }
        String value = queue.pop();
        queueRepository.updateQueue(queue);
        return value;
    }

    /**
     * Appends the given user to the back of the event's queue.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the user's auth token; must not be null
     * @throws IllegalArgumentException  if {@code eventId} or {@code token} is null
     * @throws QueueNotFoundException    if no queue exists for the given event
     * @throws QueueIsFullException      if the queue has reached its capacity
     * @throws AlreadyInQueueException   if the token is already waiting in the queue
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void pushToEventQueue(UUID eventId, String token) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        if (queue.isFull()) {
            throw new QueueIsFullException("Event queue is full (eventId: " + eventId + ")");
        }
        if (queue.contains(token)) {
            throw new AlreadyInQueueException("Token " + token + " is already in the queue for eventId: " + eventId);
        }
        queue.push(token);
        queueRepository.updateQueue(queue);
        advanceEventQueue(eventId);
    }

    @Override
    public boolean canAccessWebsite() {
        return acceptedTokens.size() < MAX_VISITORS;
    }
}
