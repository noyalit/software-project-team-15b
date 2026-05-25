package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;

/**
 * Domain service for managing event lotteries.
 *
 * <p>Owns the {@link ILotteryRepository} aggregate, the in-memory winners map,
 * and the lifecycle (create → enter → draw → access window). All lottery
 * state, repository access, transactions and retry policy live in the
 * implementation. Auth validation and audit logging are the responsibility of
 * the application-layer {@code LotteryService}.
 */
public interface ILotteryDomainService {

    /**
     * Creates a new, empty lottery for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    void createEventLottery(UUID eventId);

    /**
     * Deletes the lottery associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    void deleteEventLottery(UUID eventId);

    /**
     * Enters a user into the event's lottery. Duplicate entries are silently ignored.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param userId  the unique identifier of the user; must not be null
     */
    void addToEventLottery(UUID eventId, UUID userId);


    /**
     * Runs the lottery for the given event, selecting up to {@code count} winners and
     * granting each a timed access window.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param count   the maximum number of winners to select; must not be negative
     * @return the set of selected winner UUIDs
     */
    Set<UUID> runEventLottery(UUID eventId, int count);

    /**
     * Returns {@code true} if the user currently has an active lottery-winner access window
     * for the given event.
     *
     * <p>Auth validation is the caller's responsibility; this method receives an already-resolved
     * user identity.
     *
     * @param userId  the unique identifier of the user; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user won and their access window has not yet expired
     */
    boolean hasAccess(UUID userId, UUID eventId);

    /**
     * Returns the set of all winners drawn for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return an unmodifiable set of winner UUIDs
     */
    Set<UUID> getEventLotteryWinners(UUID eventId);

    /**
     * Clears the persistent winners set on the domain entity for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    void clearEventLotteryWinners(UUID eventId);

    /**
     * Returns the lottery eligibility status for the given user and event.
     *
     * @param userId  the unique identifier of the user; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link LotteryEligibilityDTO} describing the user's eligibility
     */
    LotteryEligibilityDTO getLotteryEligibilityForEvent(UUID userId, UUID eventId);
}
