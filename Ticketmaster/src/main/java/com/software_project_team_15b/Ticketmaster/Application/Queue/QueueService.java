package com.software_project_team_15b.Ticketmaster.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Application-layer facade over the queue domain.
 *
 * <p>Coordinates auth-dependent site-queue eviction: on each scheduled tick, expired
 * tokens are removed from the domain service's admitted set and the domain service
 * fills vacated slots from the site queue. Per-event queue state, site-queue state,
 * repository access, transactions, and retry policy all live in the domain service.
 */
@Service
public class QueueService {
    private static final int SITE_QUEUE_INTERVAL = 10;


    private static final Logger AUDIT = LoggerFactory.getLogger("audit.queue");

    private final IAuth auth;
    private final IQueueDomainService queueDomainService;
    private final UserDomainService userDomainService;

    public QueueService(IQueueDomainService queueDomainService, IAuth auth, UserDomainService userDomainService) {
        this.queueDomainService = Objects.requireNonNull(queueDomainService);
        this.auth = auth;
        this.userDomainService = Objects.requireNonNull(userDomainService);
    }

    /**
     * Evicts expired tokens from the admitted set, then delegates to the domain
     * service to fill the vacated slots from the front of the site queue.
     *
     * <p>Runs on a fixed schedule every {@link #SITE_QUEUE_INTERVAL} seconds.
     * Token-validity checks are performed here (via {@link IAuth}) so that the
     * domain service stays free of auth dependencies.
     */
    @Scheduled(fixedRate = SITE_QUEUE_INTERVAL, timeUnit = TimeUnit.SECONDS)
    private void acceptUsersFromSiteQueue() {
        Set<String> acceptedTokens = queueDomainService.getAcceptedTokens();
        for (String token : acceptedTokens) {
            if (!auth.isTokenValid(token)) {
                queueDomainService.removeAcceptedToken(token);
            }
        }
        queueDomainService.acceptUsersFromSiteQueue();
    }

    private void validateToken(String token) {
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Returns a snapshot of the user's current access state for the given event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if a queue exists for the event but cannot be read
     */
    public QueueAccessDTO getQueueAccessView(String token, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            QueueAccessDTO view = queueDomainService.getQueueAccessView(token, eventId);
            AUDIT.info("op=getQueueAccessView token={} eventId={} status={}", token, eventId, view.status());
            return view;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getQueueAccessView eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param userId    the caller's user id; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     */
    public void createEventQueue(UUID userId, UUID companyId, UUID eventId, int  capacity, int max_accepted) {
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            requireEventPermissions(userId, companyId, eventId);
            queueDomainService.createEventQueue(eventId,  capacity, max_accepted);
            AUDIT.info("op=createEventQueue userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEventQueue userId={} eventId={} result=error error={}", userId, eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param userId    the caller's user id; must not be null
     * @param companyId the company that owns the event
     * @param eventId   the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     * @throws UnauthorizedException    if the caller is not a manager, owner, or founder of the event
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void deleteEventQueue(UUID userId, UUID companyId, UUID eventId) {
        try {
            if (userId == null) throw new IllegalArgumentException("userId cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            requireEventPermissions(userId, companyId, eventId);
            queueDomainService.deleteEventQueue(eventId);
            AUDIT.info("op=deleteEventQueue userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteEventQueue userId={} eventId={} result=error error={}", userId, eventId, e.getMessage());
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