package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Application service for managing lotteries associated with events.
 *
 * <p>Each event may have at most one lottery, keyed by the event's UUID.
 * Mutating operations are transactional. Methods that perform a read-then-write
 * are additionally annotated with {@link Retryable} so that transient
 * optimistic-lock conflicts are transparently retried before propagating an
 * error to the caller.
 */
@Service
public class LotteryService {

    private final ILotteryRepository lotteryRepository;

    public LotteryService(ILotteryRepository lotteryRepository) {
        this.lotteryRepository = lotteryRepository;
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
