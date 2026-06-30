package com.software_project_team_15b.Ticketmaster.Application.Queue;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.events.TempTokenAcceptedFromQueueEvent;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SiteAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SiteQueueSnapshotDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

/**
 * Application-layer facade over the queue domain.
 *
 * <p>Coordinates auth-dependent site-queue eviction: on each scheduled tick, expired
 * tokens are removed from the domain service's admitted set and the domain service
 * fills vacated slots from the site queue. Per-event queue state, site-queue state, and
 * in-memory repository access all live in the domain service. No queue state is persisted
 * to a database; it exists only for the lifetime of the running process.
 */
@Service
public class QueueService {
    private static final int SITE_QUEUE_INTERVAL = 1;


    private static final Logger AUDIT = LoggerFactory.getLogger("audit.queue");

    private final IAuth auth;
    private final IQueueDomainService queueDomainService;
    private final ApplicationEventPublisher eventPublisher;

    public QueueService(IQueueDomainService queueDomainService, IAuth auth, ApplicationEventPublisher eventPublisher) {
        this.queueDomainService = Objects.requireNonNull(queueDomainService);
        this.auth = auth;
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public SiteQueueSnapshotDTO updateSiteQueueSettings(String adminToken, int maxVisitors) {
        try {
            if (adminToken == null) throw new IllegalArgumentException("adminToken cannot be null");
            validateToken(adminToken);
            requireSystemAdmin(adminToken);
            if (maxVisitors <= 0) throw new IllegalArgumentException("maxVisitors must be positive");
            UUID userId = auth.extractUserId(adminToken);
            queueDomainService.updateSiteQueueSettings(maxVisitors);
            SiteQueueSnapshotDTO snapshot = getSiteQueueSnapshot(adminToken);
            AUDIT.info("op=updateSiteQueueSettings userId={} maxVisitors={} result=ok", userId, maxVisitors);
            return snapshot;
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateSiteQueueSettings maxVisitors={} result=error error={}", maxVisitors, e.getMessage());
            throw e;
        }
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
        Set<String> beforeAcceptedTokens = Set.copyOf(queueDomainService.getAcceptedTokens());

        for (String token : beforeAcceptedTokens) {
            if (!auth.isTokenValid(token)) {
                queueDomainService.evictSiteToken(token);
            }
        }

        queueDomainService.acceptUsersFromSiteQueue();

        Set<String> afterAcceptedTokens = queueDomainService.getAcceptedTokens();

        afterAcceptedTokens.stream()
                .filter(token -> !beforeAcceptedTokens.contains(token))
                .forEach(token ->
                        eventPublisher.publishEvent(new TempTokenAcceptedFromQueueEvent(token))
                );
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
            UUID userId = auth.extractUserId(token);
            QueueAccessDTO view = queueDomainService.getQueueAccessView(token, eventId);
            AUDIT.info("op=getQueueAccessView userId={} eventId={} status={}", userId, eventId, view.status());
            return view;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getQueueAccessView eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param token        the caller's auth token; must not be null
     * @param eventId      the unique identifier of the event; must not be null
     * @param capacity     the maximum number of users that may wait; must be non-negative
     * @param max_accepted the maximum number of simultaneously admitted users; must be non-negative
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null, or either limit is negative
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system admin
     */
    public void createEventQueue(String token, UUID eventId, int  capacity, int max_accepted) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            if (capacity < 0) throw new IllegalArgumentException("capacity cannot be negative");
            if (max_accepted < 0) throw new IllegalArgumentException("max_accepted cannot be negative");
            validateToken(token);
            requireSystemAdmin(token);
            UUID userId = auth.extractUserId(token);
            queueDomainService.createEventQueue(eventId,  capacity, max_accepted);
            AUDIT.info("op=createEventQueue userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEventQueue eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param token   the caller's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system admin
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void deleteEventQueue(String token, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            requireSystemAdmin(token);
            UUID userId = auth.extractUserId(token);
            queueDomainService.deleteEventQueue(eventId);
            AUDIT.info("op=deleteEventQueue userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteEventQueue eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Removes all users from both the waiting list and the admitted set for the given
     * event queue. Requires system-admin privileges.
     *
     * @param token   the caller's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system adminF
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void clearEventQueue(String token, UUID eventId) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(token);
            requireSystemAdmin(token);
            UUID userId = auth.extractUserId(token);
            queueDomainService.clearEventQueue(eventId);
            AUDIT.info("op=clearEventQueue userId={} eventId={} result=ok", userId, eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=clearEventQueue eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns a snapshot of the virtual queue for the given event.
     *
     * @param adminToken the caller's auth token; must belong to a system admin
     * @param eventId    the unique identifier of the event; must not be null
     * @return a {@link QueueSnapshotDTO} describing the queue's current state
     * @throws IllegalArgumentException if {@code adminToken} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system admin
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException if no queue exists for the given event
     */
    public QueueSnapshotDTO getQueueSnapshot(String adminToken, UUID eventId) {
        try {
            if (adminToken == null) throw new IllegalArgumentException("adminToken cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            validateToken(adminToken);
            requireSystemAdmin(adminToken);
            UUID userId = auth.extractUserId(adminToken);
            QueueSnapshotDTO snapshot = queueDomainService.getQueueSnapshot(eventId);
            AUDIT.info("op=getQueueSnapshot userId={} eventId={} result=ok", userId, eventId);
            return snapshot;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getQueueSnapshot eventId={} result=error error={}", eventId, e.getMessage());
            throw e;
        }
    }

    public SiteQueueSnapshotDTO getSiteQueueSnapshot(String adminToken) {
        try {
            if (adminToken == null) throw new IllegalArgumentException("adminToken cannot be null");
            validateToken(adminToken);
            requireSystemAdmin(adminToken);
            UUID userId = auth.extractUserId(adminToken);
            SiteQueueSnapshotDTO snapshot = queueDomainService.getSiteQueueSnapshot();

            int admittedCount = (int) queueDomainService.getAcceptedTokens().stream()
                    .filter(auth::isTokenValid)
                    .filter(token -> !auth.isSystemAdmin(token))
                    .count();

            snapshot = new SiteQueueSnapshotDTO(snapshot.maxVisitors(), snapshot.waitingCount(), admittedCount);
            AUDIT.info("op=getSiteQueueSnapshot userId={} result=ok", userId);
            return snapshot;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getSiteQueueSnapshot result=error error={}", e.getMessage());
            throw e;
        }
    }

    public SiteAccessDTO getSiteAccessView(String token) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            validateToken(token);
            UUID userId = auth.extractUserId(token);

            boolean admitted = queueDomainService.isSiteTokenAccepted(token);
            Integer position = admitted ? null : null;

            if (!admitted) {
                int idx = queueDomainService.getSiteQueuePosition(token);
                position = idx >= 0 ? idx : null;
            }

            SiteAccessDTO view = new SiteAccessDTO(admitted, position);
            AUDIT.info("op=getSiteAccessView userId={} admitted={} position={}", userId, admitted, position);
            return view;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getSiteAccessView result=error error={}", e.getMessage());
            throw e;
        }
    }

    /**
     * Updates the capacity and max-accepted limits of the virtual queue for the given event.
     *
     * @param token        the caller's auth token; must not be null
     * @param eventId      the unique identifier of the event; must not be null
     * @param capacity     the new maximum number of users that may wait; must be positive
     * @param max_accepted the new maximum number of simultaneously admitted users; must be positive
     * @throws IllegalArgumentException if any argument is null or any limit is not positive
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system admin
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void updateEventQueueSettings(String token, UUID eventId, int capacity, int max_accepted) {
        try {
            if (token == null) throw new IllegalArgumentException("token cannot be null");
            if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
            if (capacity < 0) throw new IllegalArgumentException("capacity cannot be <= 0");
            if (max_accepted < 0) throw new IllegalArgumentException("max_accepted cannot be <= 0");
            validateToken(token);
            requireSystemAdmin(token);
            UUID userId = auth.extractUserId(token);
            queueDomainService.updateQueueSettings(eventId, capacity, max_accepted);
            AUDIT.info("op=updateEventQueueSettings userId={} eventId={} capacity={} max_accepted={} result=ok", userId, eventId, capacity, max_accepted);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateEventQueueSettings eventId={} capacity={} max_accepted={} result=error error={}", eventId, capacity, max_accepted, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns snapshots of all virtual queues in the repository.
     *
     * @param adminToken the caller's auth token; must belong to a system admin
     * @return an unmodifiable list of {@link QueueSnapshotDTO}, one per in-memory queue
     * @throws IllegalArgumentException if {@code adminToken} is null
     * @throws InvalidTokenException    if the token is invalid or expired
     * @throws UnauthorizedException    if the caller is not a system admin
     */
    public List<QueueSnapshotDTO> getAllQueueSnapshots(String adminToken) {
        try {
            if (adminToken == null) throw new IllegalArgumentException("adminToken cannot be null");
            validateToken(adminToken);
            requireSystemAdmin(adminToken);
            UUID userId = auth.extractUserId(adminToken);
            List<QueueSnapshotDTO> snapshots = queueDomainService.getAllQueueSnapshots();
            AUDIT.info("op=getAllQueueSnapshots userId={} count={} result=ok", userId, snapshots.size());
            return snapshots;
        } catch (RuntimeException e) {
            AUDIT.warn("op=getAllQueueSnapshots result=error error={}", e.getMessage());
            throw e;
        }
    }

    private void requireSystemAdmin(String token) {
        if (!auth.isSystemAdmin(token)) {
            throw new UnauthorizedException("caller is not a system admin");
        }
    }
}
