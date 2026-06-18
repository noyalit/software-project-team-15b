package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityStatus;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service for managing event lotteries.
 *
 * <p>Owns the persistent {@link ILotteryRepository} aggregate and the lifecycle:
 * create → users enter → organizer triggers draw → winners receive a timed access
 * window → window expires. Winner state and expiry timestamps are stored inside the
 * {@link Lottery} entity. Multiple draws are permitted; each call to
 * {@link #runEventLottery} selects from whatever entries remain and resets the
 * access window.
 *
 * <p>Auth validation and audit logging are intentionally absent here — those
 * responsibilities belong to the application-layer {@code LotteryService}, which
 * holds the {@code IAuth} dependency.
 *
 * <p>Mutating operations that touch the database are transactional. Read-then-write
 * operations are additionally annotated with {@link Retryable} so that transient
 * optimistic-lock conflicts are transparently retried before propagating to the caller.
 */
@Service
public class LotteryDomainServiceImpl implements ILotteryDomainService {

    private final ILotteryRepository lotteryRepository;

    public LotteryDomainServiceImpl(ILotteryRepository lotteryRepository) {
        this.lotteryRepository = Objects.requireNonNull(lotteryRepository);
    }

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    @Override
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
    @Override
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
    @Override
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
     * Draws one random entry from the event's lottery pool, removing it and recording
     * it as a winner in the {@link Lottery} entity.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the UUID of the randomly selected entry
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    protected UUID popRandomFromEventLottery(UUID eventId) {
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
     * Draws up to {@code count} random entries from the event's lottery pool, removing each
     * and recording them as winners in the {@link Lottery} entity.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of entries to draw; must not be negative
     * @return a set of randomly selected UUIDs (size &le; {@code count})
     * @throws IllegalArgumentException if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    protected Set<UUID> popRandomFromEventLottery(UUID eventId, int count) {
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
     * <p>If the pool has fewer than {@code count} entries all remaining entries are
     * selected. An empty pool results in zero winners but still resets the access window.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to select; must not be negative
     * @param expirationTime the timestamp at which winner access should expire; must not be null and must be in the future
     * @return the set of selected winner UUIDs (may be empty if pool was empty)
     * @throws IllegalArgumentException if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    @Override
    public Set<UUID> runEventLottery(UUID eventId, int count, LocalDateTime expirationTime) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (expirationTime == null) throw new IllegalArgumentException("expirationTime cannot be null");
        if (expirationTime.isBefore(LocalDateTime.now())) throw new IllegalArgumentException("expirationTime must be in the future");

        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }

        if (lottery.isDrawn()) {
            throw new IllegalStateException("Lottery has already been drawn for eventId: " + eventId);
        }

        Set<UUID> drawn = lottery.popRandom(count);
        lottery.setExpirationTime(expirationTime);
        lotteryRepository.updateLottery(lottery);

        return drawn;
    }

    /**
     * Returns {@code true} if the user currently has an active lottery-winner access window
     * for the given event.
     *
     * @param userId  the unique identifier of the user; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user won and their access window has not yet expired
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasAccess(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            return false;
        }
        Set<UUID> winners = lottery.getWinners();
        LocalDateTime expiresAt = lottery.getExpirationTime();
        if (expiresAt == null) {
            return false;
        }
        return winners.contains(userId) && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Returns the set of all winners drawn for the given event (persistent, from the domain entity).
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return an unmodifiable set of winner UUIDs
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Override
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
     * Returns the set of all entries that were not drawn for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return an unmodifiable set of loser UUIDs
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getEventLotteryLosers(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            throw new LotteryNotFoundException("Lottery not found for eventId: " + eventId);
        }
        return Set.copyOf(lottery.getEntries());
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
    @Override
    @Transactional(readOnly = true)
    public LotteryEligibilityDTO getLotteryEligibilityForEvent(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }

        Lottery lottery = lotteryRepository.getLottery(eventId);
        if (lottery == null) {
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.NO_LOTTERY_REQUIRED);
        }

        if (!lottery.isDrawn()) {
            return new LotteryEligibilityDTO(
                    lottery.hasEntry(userId)
                            ? LotteryEligibilityStatus.LOTTERY_OPEN_ENTERED
                            : LotteryEligibilityStatus.LOTTERY_OPEN_NOT_ENTERED
            );
        }

        Set<UUID> eventWinners = lottery.getWinners();
        if (!eventWinners.contains(userId)) {
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.NOT_SELECTED);
        }

        LocalDateTime expiry = lottery.getExpirationTime();
        if (expiry == null || LocalDateTime.now().isBefore(expiry)) {
            return new LotteryEligibilityDTO(LotteryEligibilityStatus.WON_AND_ACCESS_VALID);
        }
        return new LotteryEligibilityDTO(LotteryEligibilityStatus.ACCESS_EXPIRED);
    }
}
