package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SiteQueueSnapshotDTO;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * Domain service for managing virtual queues associated with events and the site-wide
 * waiting queue.
 *
 * <p>Owns the persistent {@link IQueueRepository} aggregate, the in-memory site queue
 * ({@link #siteQueue}), and the admitted-token set ({@link #acceptedTokens}). Per-event
 * admission state is persisted inside each {@link VirtualQueue}'s {@code accessMap}.
 * Each event may have at most one queue, keyed by the event's UUID.
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

    private static final int ACCESS_TIME = 100;
    private static final int EVENT_QUEUE_INTERVAL = 5;

    private int maxVisitors = 100;

    private final IQueueRepository queueRepository;

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
        while (!siteQueue.isEmpty() && acceptedTokens.size() < maxVisitors) {
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

    @Override
    public synchronized SiteQueueSnapshotDTO getSiteQueueSnapshot() {
        return new SiteQueueSnapshotDTO(maxVisitors, siteQueue.size(), acceptedTokens.size());
    }

    @Override
    public synchronized void updateSiteQueueSettings(int maxVisitors) {
        if (maxVisitors <= 0) throw new IllegalArgumentException("maxVisitors must be positive");
        this.maxVisitors = maxVisitors;
    }

    /**
     * Iterates every persisted queue, evicts expired access entries, and promotes waiting
     * users into the admitted set until each queue's {@code maxAccepted} cap is reached.
     * Newly admitted tokens receive an expiry window of {@link #ACCESS_TIME} seconds from now.
     *
     * <p>Runs on a fixed schedule every {@link #EVENT_QUEUE_INTERVAL} seconds.
     */
    @Scheduled(fixedRate = EVENT_QUEUE_INTERVAL, timeUnit = TimeUnit.SECONDS)
    @Transactional
    protected void advanceEventQueues() {
        for (VirtualQueue queue : queueRepository.getAllQueues()) {
            queue.advanceQueue(LocalDateTime.now().plusSeconds(ACCESS_TIME));
            queueRepository.updateQueue(queue);
        }
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
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }

        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.NO_QUEUE, null, null);
        }

        LocalDateTime expiresAt = queue.hasAccess(token);
        if (expiresAt != null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.ADMITTED, null, expiresAt);
        }

        int position = getPositionInEventQueue(token, eventId);     // throws if token not in queue
        return new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, position, null);
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * <p>If the event has no queue, {@link QueueAccessStatus#NO_QUEUE} is returned
     * immediately. If the user is already admitted, their current {@link QueueAccessDTO}
     * is returned without re-queuing them.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current access state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws QueueIsFullException     if the persistent queue has reached its capacity
     * @throws AlreadyInQueueException  if the user is already waiting in the queue
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.NO_QUEUE, null, null);
        }
        if (queue.hasAccess(token) == null) {
            pushToEventQueue(eventId, token);
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
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            return true;
        }
        return queue.hasAccess(token) != null;
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    @Override
    @Transactional
    public void createEventQueue(UUID eventId, int capacity, int max_accepted) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        if (max_accepted < 0) {
            throw new IllegalArgumentException("max_accepted cannot be negative");
        }
        VirtualQueue queue = new VirtualQueue(eventId, capacity, max_accepted);
        queueRepository.addQueue(queue);
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
    }

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors,
     * i.e. the number of admitted tokens is below {@link #MAX_VISITORS}.
     *
     * @return {@code true} if below the cap; {@code false} if at or above it
     */
    @Override
    public boolean canAccessWebsite() {
        return acceptedTokens.size() < MAX_VISITORS;
    }

    /**
     * Removes all users from both the waiting list and the admitted set for the given
     * event queue, leaving it completely empty.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Override
    @Transactional
    public void clearEventQueue(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        queue.clear();
        queueRepository.updateQueue(queue);
    }

    /**
     * Returns a snapshot of the virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueSnapshotDTO} describing the queue's current state
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Override
    public QueueSnapshotDTO getQueueSnapshot(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        return toSnapshot(queue);
    }

    /**
     * Returns snapshots of all virtual queues currently in the repository.
     *
     * @return an unmodifiable list of {@link QueueSnapshotDTO}, one per persisted queue
     */
    @Override
    public List<QueueSnapshotDTO> getAllQueueSnapshots() {
        return queueRepository.getAllQueues().stream()
                .map(this::toSnapshot)
                .toList();
    }

    /**
     * Updates the capacity and max-accepted limits of the virtual queue for the given event.
     *
     * @param eventId      the unique identifier of the event; must not be null
     * @param capacity     the new maximum number of users that may wait; must be non-negative
     * @param max_accepted the new maximum number of simultaneously admitted users; must be non-negative
     * @throws IllegalArgumentException if {@code eventId} is null or either limit is negative
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Override
    @Transactional
    public void updateQueueSettings(UUID eventId, int capacity, int max_accepted) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        if (max_accepted < 0) {
            throw new IllegalArgumentException("max_accepted cannot be negative");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        queue.setSettings(capacity, max_accepted);
        queueRepository.updateQueue(queue);
    }

    private QueueSnapshotDTO toSnapshot(VirtualQueue queue) {
        Map<String, LocalDateTime> admitted = queue.getAccessMap();
        return new QueueSnapshotDTO(
                queue.getId(),
                queue.getCapacity(),
                queue.getMaxAccepted(),
                queue.size(),
                admitted.size(),
                admitted
        );
    }
}
