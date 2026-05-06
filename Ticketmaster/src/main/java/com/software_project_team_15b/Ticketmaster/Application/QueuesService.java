package com.software_project_team_15b.Ticketmaster.Application;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.*;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Application service for managing virtual queues and lotteries associated with events.
 *
 * <p>Each event may have at most one queue and one lottery, both keyed by the event's UUID.
 * All mutating operations are transactional. Methods that perform a read-then-write are
 * additionally annotated with {@link Retryable} so that transient optimistic-lock conflicts
 * (arising when multiple threads update the same aggregate concurrently) are transparently
 * retried before propagating an error to the caller.
 */
@Service
public class QueuesService {
    private final IQueueRepository queueRepository;
    private final ILotteryRepository lotteryRepository;
    private final IAuth auth;
    private final Queue<String> siteQueue = new LinkedList<>();

    public QueuesService(IQueueRepository queueRepository, ILotteryRepository lotteryRepository, IAuth auth) {
        this.queueRepository = queueRepository;
        this.lotteryRepository = lotteryRepository;
        this.auth = auth;
    }

    public void addUserToSiteQueue(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (siteQueue.contains(token)) {
            throw new IllegalArgumentException("User with token " + token + " is already in the site queue");
        }
        siteQueue.add(token);
    }

    public String getNextUserFromSiteQueue() {
        String token = siteQueue.poll();
        while (!auth.isTokenValid(token) && !siteQueue.isEmpty()) {
            token = siteQueue.poll();
        }
        if (token == null) {
            throw new EmptyQueueException("Site queue is empty");
        }
        return token;
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
    }

    /**
     * Removes and returns the user ID at the front of the event's queue (FIFO order).
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the UUID of the user at the front of the queue
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws EmptyQueueException      if the queue contains no entries
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public UUID popFromEventQueue(UUID eventId) {
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
        UUID value = queue.pop();
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
     * @param userId  the unique identifier of the user to enqueue; must not be null
     * @throws IllegalArgumentException if {@code eventId} or {@code userId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void pushToEventQueue(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
        }
        if (queue.isFull()) {
            throw new QueueIsFullException("Event queue is full (eventId: " + eventId + ")");
        }
        queue.push(userId);
        queueRepository.updateQueue(queue);
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
