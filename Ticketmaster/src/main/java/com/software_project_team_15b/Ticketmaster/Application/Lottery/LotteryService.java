package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;

import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
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
    private final UserDomainService userDomainService;

    public LotteryService(ILotteryDomainService lotteryDomainService, UserDomainService userDomainService) {
        this.lotteryDomainService = Objects.requireNonNull(lotteryDomainService);
        this.userDomainService = Objects.requireNonNull(userDomainService);
    }

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param userId    the caller's user id; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     */
    public void createEventLottery(UUID userId, UUID companyId, UUID eventId) {
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            requireEventPermissions(userId, companyId, eventId);
            lotteryDomainService.createEventLottery(eventId);
            AUDIT.info("op=createEventLottery userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEventLottery userId={} eventId={} result=error error={}", userId, eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param userId    the caller's user id; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void deleteEventLottery(UUID userId, UUID companyId, UUID eventId) {
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            requireEventPermissions(userId, companyId, eventId);
            lotteryDomainService.deleteEventLottery(eventId);
            AUDIT.info("op=deleteEventLottery userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteEventLottery userId={} eventId={} result=error error={}", userId, eventId, e.getMessage());
            throw e;
        }
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
        try {
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            lotteryDomainService.addToEventLottery(eventId, userId);
            AUDIT.info("op=addToEventLottery eventId={} userId={} result=ok", eventId, userId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=addToEventLottery eventId={} userId={} result=error error={}", eventId, userId, e.getMessage());
            throw e;
        }
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
    public Set<UUID> runEventLottery(UUID userId, UUID companyId, UUID eventId, int count) {
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            if (count < 0) throw new IllegalArgumentException("count cannot be negative");

            requireEventPermissions(userId, companyId, eventId);

            Set<UUID> drawn = lotteryDomainService.runEventLottery(eventId, count);
            AUDIT.info("op=runEventLottery eventId={} count={} winnersDrawn={} result=ok", eventId, count, drawn.size());
            return drawn;
        } catch (RuntimeException e) {
            AUDIT.warn("op=runEventLottery eventId={} count={} result=error error={}", eventId, count, e.getMessage());
            throw e;
        }
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
        try {
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            Set<UUID> winners = lotteryDomainService.getEventLotteryWinners(eventId);
            AUDIT.info("op=getEventLotteryWinners eventId={} result=ok", eventId);
            return winners;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getEventLotteryWinners eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
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
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            LotteryEligibilityDTO dto = lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId);
            AUDIT.info("op=getLotteryEligibilityForEvent userId={} eventId={} status={}", userId, eventId, dto.status());
            return dto;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getLotteryEligibilityForEvent userId={} eventId={} result=error error={}", userId, eventId, e.getMessage());
            throw e;
        }
    }

    private void requireEventPermissions(UUID userId, UUID companyId, UUID eventId) {
        if (!userDomainService.isActiveManager(userId, companyId, eventId) &&
            !userDomainService.isActiveOwner(userId, companyId) &&
            !userDomainService.isActiveFounder(userId, companyId)) {
            throw new UnauthorizedException("user does not have permission to perform this action");
        }
    }
}