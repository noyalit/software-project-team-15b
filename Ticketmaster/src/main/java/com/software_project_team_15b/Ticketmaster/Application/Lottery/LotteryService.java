package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for managing lotteries associated with events.
 *
 * <p>Each event may have at most one lottery, keyed by the event's UUID. The lottery
 * lifecycle is: create → users enter → organizer triggers draw → winners get a
 * timed access window → window expires. Once drawn, no further drawing occurs.
 *
 * <p>Mutating operations that touch the database are transactional. Read-then-write
 * operations are also annotated with {@link Retryable} so that transient
 * optimistic-lock conflicts are transparently retried before propagating to the caller.
 */
@Service
public class LotteryService {

    private static final int WINNER_ACCESS_TIME = 600; // seconds

    private final ILotteryRepository lotteryRepository;
    private final IAuth auth;
    // eventId → (winnerId → accessExpiresAt). Presence of an eventId key signals the lottery was drawn.
    // Entries are never removed; getLotteryEligibilityForEvent checks the timestamp to distinguish
    // WON_AND_ACCESS_VALID from ACCESS_EXPIRED without needing a scheduler.
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>> winners = new ConcurrentHashMap<>();
    // Self-reference through the Spring proxy so that @Retryable and @Transactional
    // on drawWinnersTransactional take effect when called from runEventLottery.
    @Autowired @Lazy private LotteryService self;

    public LotteryService(ILotteryRepository lotteryRepository, IAuth auth) {
        this.lotteryRepository = lotteryRepository;
        this.auth = auth;
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
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
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
     * Enters a user into the event's lottery. Duplicate entries are silently ignored.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param userId  the unique identifier of the user; must not be null
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
     * Draws one random entry from the event's lottery pool, removing it.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the UUID of the randomly selected entry
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
     * Draws up to {@code count} random entries from the event's lottery pool, removing each.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of entries to draw; must not be negative
     * @return a set of randomly selected UUIDs (size &le; {@code count})
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

    /**
     * Runs the lottery for the given event, selecting up to {@code count} winners.
     *
     * <p>Winners are granted a {@link #WINNER_ACCESS_TIME}-second window to purchase
     * tickets. When that window closes each winner is removed from the admitted set
     * automatically; no further drawing occurs. The lottery may be run at most once
     * per event — subsequent calls throw {@link LotteryAlreadyDrawnException}.
     *
     * <p>If the lottery pool has fewer than {@code count} entries all remaining
     * entries are selected. An empty pool results in zero winners; the lottery is
     * still marked as drawn so it cannot be triggered again.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to select; must not be negative
     * @return the set of selected winner UUIDs (may be empty if pool was empty)
     * @throws IllegalArgumentException    if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException    if no lottery exists for the given event
     * @throws LotteryAlreadyDrawnException if the lottery for this event has already been drawn
     */
    public synchronized Set<UUID> runEventLottery(UUID eventId, int count) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        if (winners.containsKey(eventId)) {
            throw new LotteryAlreadyDrawnException("Lottery for event " + eventId + " has already been drawn");
        }

        Set<UUID> drawn = self.drawWinnersTransactional(eventId, count);

        // Mark as drawn before scheduling — always, even when drawn is empty.
        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = new ConcurrentHashMap<>();
        winners.put(eventId, eventWinners);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(WINNER_ACCESS_TIME);
        for (UUID winner : drawn) {
            eventWinners.put(winner, expiresAt);
        }

        return drawn;
    }

    /**
     * Internal transactional draw called through the Spring proxy so that
     * {@link Retryable} and {@link Transactional} take effect.
     *
     * @param eventId the unique identifier of the event
     * @param count   maximum number of winners to draw
     * @return the set of drawn winner UUIDs (may be empty)
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public Set<UUID> drawWinnersTransactional(UUID eventId, int count) {
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        Set<UUID> drawn = lottery.popRandom(count);
        lotteryRepository.updateLottery(lottery);
        return drawn;
    }

    /**
     * Forcibly revokes a winner's access for the given event (e.g. for admin intervention).
     * After this call {@link #getLotteryEligibilityForEvent} returns
     * {@link LotteryEligibilityStatus#NOT_SELECTED} for that user.
     * No new drawing is triggered — once a lottery is drawn it is permanently closed.
     *
     * @param userId  the winner whose access is being revoked; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     */
    protected synchronized void clearWinnerAccess(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = winners.get(eventId);
        if (eventWinners == null) return;
        eventWinners.remove(userId);
    }

    /**
     * Checks whether the authenticated user currently has active lottery-winner access for the event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user won and their access window is still open
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
        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = winners.get(eventId);
        if (eventWinners == null) return false;
        LocalDateTime expiresAt = eventWinners.get(userId);
        return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Returns the set of all winners drawn for the given event (persistent, from the domain entity).
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return an unmodifiable set of winner UUIDs
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Transactional(readOnly = true)
    public Set<UUID> getEventLotteryWinners(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        return lottery.getWinners();
    }

    /**
     * Clears the persistent winners set on the domain entity for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void clearEventLotteryWinners(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        lottery.clearWinners();
        lotteryRepository.updateLottery(lottery);
    }

    /**
     * Returns the lottery eligibility status for the given user and event.
     *
     * <ul>
     *   <li>{@link LotteryEligibilityStatus#NO_LOTTERY_REQUIRED} — no lottery has been created
     *       for the event; the user may proceed directly.</li>
     *   <li>{@link LotteryEligibilityStatus#NOT_SELECTED} — either the lottery has not been drawn
     *       yet, or it was drawn and the user was not among the winners.</li>
     *   <li>{@link LotteryEligibilityStatus#WON_AND_ACCESS_VALID} — the user won and their
     *       access window is still open.</li>
     *   <li>{@link LotteryEligibilityStatus#ACCESS_EXPIRED} — the user won but their access
     *       window has since closed.</li>
     * </ul>
     *
     * @param userId  the unique identifier of the user; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link LotteryEligibilityDTO} describing the user's eligibility
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     */
    public LotteryEligibilityDTO getLotteryEligibilityForEvent(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }

        ConcurrentHashMap<UUID, LocalDateTime> eventWinners = winners.get(eventId);

        if (eventWinners == null) {
            // Lottery has not been drawn yet — check whether one even exists.
            Lottery lottery = lotteryRepository.getLottery(eventId);
            if (lottery == null) {
                return new LotteryEligibilityDTO(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
            }
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.NOT_SELECTED);
        }

        // Lottery was drawn — check whether this user won and whether their window is still open.
        LocalDateTime expiresAt = eventWinners.get(userId);
        if (expiresAt == null) {
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.NOT_SELECTED);
        }
        if (LocalDateTime.now().isBefore(expiresAt)) {
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        }
        return new LotteryEligibilityDTO(LotteryEligibilityStatus.ACCESS_EXPIRED);
    }
}
