package com.software_project_team_15b.Ticketmaster.Application.Lottery;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;

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
    private final IAuth auth;
    private final INotifier notifier;
    @Autowired
    public LotteryService(ILotteryDomainService lotteryDomainService, UserDomainService userDomainService,  IAuth auth, INotifier notifier) {
        this.lotteryDomainService = Objects.requireNonNull(lotteryDomainService);
        this.userDomainService = Objects.requireNonNull(userDomainService);
        this.auth = Objects.requireNonNull(auth);
        this.notifier = Objects.requireNonNull(notifier);
    }

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param token     the caller's auth token; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     */
    public void createEventLottery(String token, UUID companyId, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            UUID userId = auth.extractUserId(token);
            requireEventPermissions(userId, companyId, eventId);
            lotteryDomainService.createEventLottery(eventId);
            AUDIT.info("op=createEventLottery userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEventLottery eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param token     the caller's auth token; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void deleteEventLottery(String token, UUID companyId, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            UUID userId = auth.extractUserId(token);
            requireEventPermissions(userId, companyId, eventId);
            lotteryDomainService.deleteEventLottery(eventId);
            AUDIT.info("op=deleteEventLottery userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteEventLottery eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Enters a user into the event's lottery. Duplicate entries are silently ignored.
     *
     * <p>Only members may enter ({@link IAuth#isMember} is checked). The user identity
     * is resolved from the token via {@link IAuth#extractUserId}.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the caller's auth token; must not be null
     * @throws IllegalArgumentException if {@code eventId} or {@code token} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a member
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public void addToEventLottery(UUID eventId, String token) {
        try {
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            validateToken(token);
            if (!auth.isMember(token)) throw new UnauthorizedException("only members can enter the lottery");
            UUID userId = auth.extractUserId(token);
            lotteryDomainService.addToEventLottery(eventId, userId);
            AUDIT.info("op=addToEventLottery eventId={} userId={} result=ok", eventId, userId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=addToEventLottery eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Runs the lottery for the given event, selecting up to {@code count} winners.
     *
     * @param token     the caller's auth token; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @param count     the maximum number of winners to select; must not be negative
     * @param expirationTime the timestamp at which winner access should expire; must not be null and must be in the future
     * @return the set of selected winner UUIDs (may be empty if pool was empty)
     * @throws IllegalArgumentException     if {@code token} or {@code eventId} is null, or {@code count} is negative
     * @throws InvalidTokenException        if the token is invalid or expired
     * @throws UnauthorizedException        if the caller is not a manager, owner, or founder of the event
     * @throws LotteryNotFoundException     if no lottery exists for the given event
     * @throws LotteryAlreadyDrawnException if the lottery for this event has already been drawn
     */
    public Set<UUID> runEventLottery(String token, UUID companyId, UUID eventId, int count, LocalDateTime expirationTime) {

        try {
            if (token == null)
                throw new IllegalArgumentException("token cannot be null");

            if (eventId == null)
                throw new IllegalArgumentException("eventId cannot be null");

            if (count < 0)
                throw new IllegalArgumentException("count cannot be negative");

            if (expirationTime == null)
                throw new IllegalArgumentException("expirationTime cannot be null");

            if (expirationTime.isBefore(LocalDateTime.now()))
                throw new IllegalArgumentException("expirationTime must be in the future");

            validateToken(token);

            UUID userId = auth.extractUserId(token);

            requireEventPermissions(userId, companyId, eventId);

            Set<UUID> drawn =
                    lotteryDomainService.runEventLottery(
                            eventId,
                            count,
                            expirationTime
                    );

            Set<UUID> losers;
            try {
                losers = Objects.requireNonNullElse(
                        lotteryDomainService.getEventLotteryLosers(eventId),
                        Set.of()
                );
            } catch (RuntimeException e) {
                AUDIT.warn(
                        "op=getEventLotteryLosers eventId={} result=error reason={} ",
                        eventId,
                        e.getMessage()
                );
                losers = Set.of();
            }

            AUDIT.info(
                    "op=runEventLottery eventId={} count={} winnersDrawn={} result=ok",
                    eventId,
                    count,
                    drawn.size()
            );

            // notify winners
            NotificationDTO winnerNotification =
                    new NotificationDTO(
                            NotificationType.LOTTERY_WON,
                            "You won the lottery",
                            "You won the lottery for event "
                                    + eventId
                                    + ". Access expires at "
                                    + expirationTime,
                            Instant.now()
                    );

            for (UUID winner : drawn) {

                try {

                    notifier.notifyUser(winner, winnerNotification);

                } catch (RuntimeException e) {

                    AUDIT.warn(
                            "op=notifyLotteryWinner userId={} eventId={} result=error reason={}",
                            winner,
                            eventId,
                            e.getMessage()
                    );
                }
            }

            // notify losers
            NotificationDTO loserNotification =
                    new NotificationDTO(
                            NotificationType.LOTTERY_LOST,
                            "Lottery results",
                            "You were not selected in the lottery for event "
                                    + eventId
                                    + ".",
                            Instant.now()
                    );

            for (UUID loser : losers) {

                try {

                    notifier.notifyUser(loser, loserNotification);

                } catch (RuntimeException e) {

                    AUDIT.warn(
                            "op=notifyLotteryLoser userId={} eventId={} result=error reason={}",
                            loser,
                            eventId,
                            e.getMessage()
                    );
                }
            }

            return drawn;
        } catch (RuntimeException e) {
            AUDIT.warn("op=runEventLottery eventId={} count={} result=error error={}", eventId, count, e.getMessage());
            throw e;
        }

    }
    

    public Set<String> runEventLotteryUsernames(String token, UUID companyId, UUID eventId, int count, LocalDateTime expirationTime) {
        Set<UUID> winners = runEventLottery(token, companyId, eventId, count, expirationTime);
        return winners.stream()
                .map(id -> {
                    try {
                        return userDomainService.resolveMemberById(id).getUsername();
                    } catch (RuntimeException e) {
                        return id.toString();
                    }
                })
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns the set of all winners drawn for the given event.
     *
     * @param token     the caller's auth token; must not be null
     * @param companyId the company that owns the event; must not be null
     * @param eventId   the unique identifier of the event; must not be null
     * @return an unmodifiable set of winner UUIDs
     * @throws IllegalArgumentException if {@code token}, {@code companyId}, or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     * @throws LotteryNotFoundException if no lottery exists for the given event
     */
    public Set<UUID> getEventLotteryWinners(String token, UUID companyId, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            UUID userId = auth.extractUserId(token);
            requireEventPermissions(userId, companyId, eventId);
            Set<UUID> winners = lotteryDomainService.getEventLotteryWinners(eventId);
            AUDIT.info("op=getEventLotteryWinners userId={} eventId={} result=ok", userId, eventId);
            return winners;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getEventLotteryWinners eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    public Set<String> getEventLotteryWinnerUsernames(String token, UUID companyId, UUID eventId) {
        Set<UUID> winners = getEventLotteryWinners(token, companyId, eventId);
        return winners.stream()
                .map(id -> {
                    try {
                        return userDomainService.resolveMemberById(id).getUsername();
                    } catch (RuntimeException e) {
                        return id.toString();
                    }
                })
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns the lottery eligibility status for the given user and event.
     *
     * @param token   the caller's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link LotteryEligibilityDTO} describing the user's eligibility
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     */
    public LotteryEligibilityDTO getLotteryEligibilityForEvent(String token, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            UUID userId = auth.extractUserId(token);
            LotteryEligibilityDTO dto = lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId);
            AUDIT.info("op=getLotteryEligibilityForEvent userId={} eventId={} status={}", userId, eventId, dto.status());
            return dto;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getLotteryEligibilityForEvent eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    private void validateToken(String token) {
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    private void requireEventPermissions(UUID userId, UUID companyId, UUID eventId) {
        if (!userDomainService.isActiveManager(userId, companyId, eventId) &&
            !userDomainService.isActiveCompanyManager(userId, companyId) &&
            !userDomainService.isActiveOwner(userId, companyId) &&
            !userDomainService.isActiveFounder(userId, companyId)) {
            throw new UnauthorizedException("user does not have permission to perform this action");
        }
    }
}
