package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Application-layer facade over the lottery domain.
 *
 * <p>Holds only the {@link ILotteryDomainService} and forwards every call to it.
 * All lottery state, repository access, scheduling, transactions and retry policy
 * live in the domain service; this class exists as the application-layer entry
 * point for lottery operations so that callers in the application layer never
 * need to depend on another application service to use lottery functionality.
 */
@Service
public class LotteryService {

    private final ILotteryDomainService lotteryDomainService;

    public LotteryService(ILotteryDomainService lotteryDomainService) {
        this.lotteryDomainService = Objects.requireNonNull(lotteryDomainService);
    }

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    public void createEventLottery(UUID eventId) {
        lotteryDomainService.createEventLottery(eventId);
    }

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void deleteEventLottery(UUID eventId) {
        lotteryDomainService.deleteEventLottery(eventId);
    }

    /**
     * Enters a user into the event's lottery. Duplicate entries are silently ignored.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param userId  the unique identifier of the user; must not be null
     * @throws IllegalArgumentException if {@code eventId} or {@code userId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void addToEventLottery(UUID eventId, UUID userId) {
        lotteryDomainService.addToEventLottery(eventId, userId);
    }

    /**
     * Draws one random entry from the event's lottery pool, removing it.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the UUID of the randomly selected entry
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    public UUID popRandomFromEventLottery(UUID eventId) {
        return lotteryDomainService.popRandomFromEventLottery(eventId);
    }

    /**
     * Draws up to {@code count} random entries from the event's lottery pool, removing each.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of entries to draw; must not be negative
     * @return a set of randomly selected UUIDs (size &le; {@code count})
     * @throws IllegalArgumentException if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException if no lottery exists for the given event
     * @throws EmptyLotteryException    if the lottery contains no entries
     */
    public Set<UUID> popRandomFromEventLottery(UUID eventId, int count) {
        return lotteryDomainService.popRandomFromEventLottery(eventId, count);
    }

    /**
     * Runs the lottery for the given event, selecting up to {@code count} winners.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to select; must not be negative
     * @return the set of selected winner UUIDs (may be empty if pool was empty)
     * @throws IllegalArgumentException    if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException    if no lottery exists for the given event
     * @throws LotteryAlreadyDrawnException if the lottery for this event has already been drawn
     */
    public Set<UUID> runEventLottery(UUID eventId, int count) {
        return lotteryDomainService.runEventLottery(eventId, count);
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
        return lotteryDomainService.hasAccess(token, eventId);
    }

    /**
     * Returns the set of all winners drawn for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return an unmodifiable set of winner UUIDs
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public Set<UUID> getEventLotteryWinners(UUID eventId) {
        return lotteryDomainService.getEventLotteryWinners(eventId);
    }

    /**
     * Clears the persistent winners set on the domain entity for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void clearEventLotteryWinners(UUID eventId) {
        lotteryDomainService.clearEventLotteryWinners(eventId);
    }

    /**
     * Returns the lottery eligibility status for the given user and event.
     *
     * @param userId  the unique identifier of the user; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link LotteryEligibilityDTO} describing the user's eligibility
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     */
    public LotteryEligibilityDTO getLotteryEligibilityForEvent(UUID userId, UUID eventId) {
        return lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId);
    }
}
