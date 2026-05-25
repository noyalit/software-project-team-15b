package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyLotteryException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Application-layer facade over the lottery domain.
 *
 * <p>Owns auth validation and audit logging. All lottery state, repository
 * access, transactions and retry policy live in the domain service; this class
 * validates inputs, resolves the caller identity via {@link IAuth}, logs each
 * operation, and delegates to {@link ILotteryDomainService}.
 */
@Service
public class LotteryService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.lottery");

    private final ILotteryDomainService lotteryDomainService;
    private final IAuth auth;

    public LotteryService(ILotteryDomainService lotteryDomainService, IAuth auth) {
        this.lotteryDomainService = Objects.requireNonNull(lotteryDomainService);
        this.auth = Objects.requireNonNull(auth);
    }

    private void validateToken(String token) {
        if (!auth.isTokenValid(token)) {
            AUDIT.warn("op=validateToken result=rejected reason=invalid_token");
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    public void createEventLottery(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=createEventLottery eventId={}", eventId);
        lotteryDomainService.createEventLottery(eventId);
        AUDIT.info("op=createEventLottery eventId={} result=ok", eventId);
    }

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void deleteEventLottery(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=deleteEventLottery eventId={}", eventId);
        lotteryDomainService.deleteEventLottery(eventId);
        AUDIT.info("op=deleteEventLottery eventId={} result=ok", eventId);
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
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        AUDIT.info("op=addToEventLottery eventId={} userId={}", eventId, userId);
        lotteryDomainService.addToEventLottery(eventId, userId);
        AUDIT.info("op=addToEventLottery eventId={} userId={} result=ok", eventId, userId);
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
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=popRandomFromEventLottery eventId={}", eventId);
        UUID result = lotteryDomainService.popRandomFromEventLottery(eventId);
        AUDIT.info("op=popRandomFromEventLottery eventId={} result=ok", eventId);
        return result;
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
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (count < 0) throw new IllegalArgumentException("count cannot be negative");
        AUDIT.info("op=popRandomFromEventLottery eventId={} count={}", eventId, count);
        Set<UUID> results = lotteryDomainService.popRandomFromEventLottery(eventId, count);
        AUDIT.info("op=popRandomFromEventLottery eventId={} count={} result=ok", eventId, count);
        return results;
    }

    /**
     * Runs the lottery for the given event, selecting up to {@code count} winners.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to select; must not be negative
     * @return the set of selected winner UUIDs (may be empty if pool was empty)
     * @throws IllegalArgumentException     if {@code eventId} is null or {@code count} is negative
     * @throws LotteryNotFoundException     if no lottery exists for the given event
     * @throws LotteryAlreadyDrawnException if the lottery for this event has already been drawn
     */
    public Set<UUID> runEventLottery(UUID eventId, int count) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (count < 0) throw new IllegalArgumentException("count cannot be negative");
        AUDIT.info("op=runEventLottery eventId={} count={}", eventId, count);
        Set<UUID> drawn = lotteryDomainService.runEventLottery(eventId, count);
        AUDIT.info("op=runEventLottery eventId={} count={} winnersDrawn={} result=ok", eventId, count, drawn.size());
        return drawn;
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
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID userId = auth.extractUserId(token);
        AUDIT.info("op=hasAccess userId={} eventId={}", userId, eventId);
        boolean result = lotteryDomainService.hasAccess(userId, eventId);
        AUDIT.info("op=hasAccess userId={} eventId={} result={}", userId, eventId, result);
        return result;
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
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=getEventLotteryWinners eventId={}", eventId);
        Set<UUID> winners = lotteryDomainService.getEventLotteryWinners(eventId);
        AUDIT.info("op=getEventLotteryWinners eventId={} result=ok", eventId);
        return winners;
    }

    /**
     * Clears the persistent winners set on the domain entity for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void clearEventLotteryWinners(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=clearEventLotteryWinners eventId={}", eventId);
        lotteryDomainService.clearEventLotteryWinners(eventId);
        AUDIT.info("op=clearEventLotteryWinners eventId={} result=ok", eventId);
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
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=getLotteryEligibilityForEvent userId={} eventId={}", userId, eventId);
        LotteryEligibilityDTO dto = lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId);
        AUDIT.info("op=getLotteryEligibilityForEvent userId={} eventId={} status={}", userId, eventId, dto.status());
        return dto;
    }
}
