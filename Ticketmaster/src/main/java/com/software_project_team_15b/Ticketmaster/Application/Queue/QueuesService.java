package com.software_project_team_15b.Ticketmaster.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.*;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

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
 * Application service for managing virtual queues and lotteries associated with events.
 *
 * <p>Each event may have at most one queue and one lottery, both keyed by the event's UUID.
 * Mutating operations on persistent queues and lotteries are transactional. Methods that
 * perform a read-then-write are additionally annotated with {@link Retryable} so that
 * transient optimistic-lock conflicts (arising when multiple threads update the same
 * aggregate concurrently) are transparently retried before propagating an error to the caller.
 */
@Service
public class QueuesService {
    private final int ACCESS_TIME = 100;

    private final IQueueRepository queueRepository;
    private final ILotteryRepository lotteryRepository;
    private final IAuth auth;
    // Self-reference through the Spring proxy so that @Retryable and @Transactional
    // on popFromEventQueue take effect when called from advanceEventQueue.
    @Autowired @Lazy private QueuesService self;
    private final Queue<String> siteQueue = new LinkedList<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>> eventAccess = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public QueuesService(IQueueRepository queueRepository, ILotteryRepository lotteryRepository, IAuth auth) {
        this.queueRepository = queueRepository;
        this.lotteryRepository = lotteryRepository;
        this.auth = auth;
    }

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null or is already present in the queue
     */
    public synchronized void addUserToSiteQueue(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (siteQueue.contains(token)) {
            throw new IllegalArgumentException("User with token " + token + " is already in the site queue");
        }
        siteQueue.add(token);
    }

    /**
     * Removes and returns the first valid token from the front of the site-wide queue,
     * skipping any expired or invalid tokens encountered along the way.
     *
     * @return the next valid auth token
     * @throws EmptyQueueException if the queue is empty or contains no valid tokens
     */
    public synchronized String getNextUserFromSiteQueue() {
        String token = siteQueue.poll();
        while (token != null && !auth.isTokenValid(token)) {
            token = siteQueue.poll();
        }
        if (token == null || !auth.isTokenValid(token)) {
            throw new EmptyQueueException("Site queue is empty or contains no valid tokens");
        }
        return token;
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

    protected synchronized void clearEventAccess(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        access.remove(userId);
        advanceEventQueue(eventId);
    }

    protected synchronized void advanceEventQueue(UUID eventId) {
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        while (access.size() < 100) {
            try {
                String nextToken = self.popFromEventQueue(eventId);
                UUID nextUser = auth.extractUserId(nextToken);
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ACCESS_TIME);
                access.put(nextUser, expiresAt);
                scheduler.schedule(() -> clearEventAccess(nextUser, eventId), ACCESS_TIME, TimeUnit.SECONDS);
            } catch (EmptyQueueException e) {
                break;
            }
        }
    }

    /**
     * Returns {@code true} if the user identified by {@code token} is currently in the
     * admitted window for the given event.
     *
     * <p><b>Note:</b> there is a small window between when a user's access time expires and
     * when the background scheduler removes them from the admitted set. During this window
     * this method may return {@code true} even though the access has technically expired.
     * Callers that need strict expiry enforcement should use
     * {@link #getQueueAccessView(String, UUID)} and check
     * {@link QueueAccessView#canCreateActiveOrder()} instead.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     */
    public boolean hasAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        UUID userId = auth.extractUserId(token);
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        return access != null && access.containsKey(userId);
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
     * @return a {@link QueueAccessView} describing the user's current state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null,
     *                                  or if the user is not present in the queue (when WAITING)
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if a queue exists for the event but cannot be read
     */
    public QueueAccessView getQueueAccessView(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        UUID userId = auth.extractUserId(token);
        ConcurrentHashMap<UUID, LocalDateTime> admittedUsers = eventAccess.get(eventId);
        if (admittedUsers == null) {
            return new QueueAccessView(eventId, QueueAccessStatus.NO_QUEUE, null, null);
        }
        LocalDateTime expiresAt = admittedUsers.get(userId);
        if (expiresAt != null) {
            return new QueueAccessView(eventId, QueueAccessStatus.ADMITTED, null, expiresAt);
        }
        int position = getPositionInEventQueue(token, eventId);
        return new QueueAccessView(eventId, QueueAccessStatus.WAITING, position, null);
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * <p>If the user is already admitted (promoted from the queue and their window has not
     * yet expired), this method returns their current {@link QueueAccessView} immediately
     * without re-queuing them. Re-queuing an admitted user would corrupt state: because
     * the user was already popped from the persistent queue, {@code pushToEventQueue}
     * would not detect the duplicate and would silently add them a second time.
     *
     * <p>If the user is not yet enrolled they are appended to the back of the queue.
     * Depending on how many admission slots are currently free, the returned view will
     * have status {@link QueueAccessStatus#ADMITTED} (promoted immediately) or
     * {@link QueueAccessStatus#WAITING} (all 100 slots occupied).
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessView} describing the user's current access state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws QueueIsFullException     if the persistent queue has reached its capacity
     * @throws AlreadyInQueueException  if the user is already waiting in the queue
     */
    public QueueAccessView requestAccess(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        UUID userId = auth.extractUserId(token);
        ConcurrentHashMap<UUID, LocalDateTime> admittedUsers = eventAccess.get(eventId);
        if (admittedUsers != null && admittedUsers.containsKey(userId)) {
            return getQueueAccessView(token, eventId);
        }
        self.pushToEventQueue(eventId, token);
        return getQueueAccessView(token, eventId);
    }


    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
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
    @Transactional
    public void deleteEventQueue(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if  (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        queueRepository.removeQueue(queue);
        eventAccess.remove(eventId);
    }

    /**
     * Removes and returns the user ID at the front of the event's queue (FIFO order).
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

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    @Transactional
    public void createEventLottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = new Lottery(eventId);
        lotteryRepository.addLottery(lottery);
    }

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException  if {@code eventId} is null
     * @throws LotteryNotFoundException  if no lottery exists for the given event
     */
    @Transactional
    public void deleteEventLottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        lotteryRepository.removeLottery(lottery);
    }

    /**
     * Enters a user into the event's lottery. Duplicate entries for the same user are silently ignored.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param userId  the unique identifier of the user to enter; must not be null
     * @throws IllegalArgumentException if {@code eventId} or {@code userId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void addToEventLottery(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        lottery.add(userId);
        lotteryRepository.updateLottery(lottery);
    }

    /**
     * Draws one random winner from the event's lottery, removing them from the pool.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the UUID of the randomly selected winner
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public UUID popRandomFromEventLottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        UUID value = lottery.popRandom();
        if (value == null) {
            throw new EmptyLotteryException("Event lottery is empty (eventId: " + eventId + ")");
        }
        lotteryRepository.updateLottery(lottery);
        return value;
    }

    /**
     * Draws up to {@code count} random winners from the event's lottery, removing each
     * from the pool. If the lottery has fewer than {@code count} entries, all remaining
     * entries are returned.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to draw; must not be negative
     * @return a set of randomly selected winner UUIDs (size &le; {@code count})
     * @throws IllegalArgumentException if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public Set<UUID> popRandomFromEventLottery(UUID eventId, int count) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        Set<UUID> values = lottery.popRandom(count);
        if (values.isEmpty()) {
            throw new EmptyLotteryException("Event lottery is empty (eventId: " + eventId + ")");
        }
        lotteryRepository.updateLottery(lottery);
        return values;
    }
}
