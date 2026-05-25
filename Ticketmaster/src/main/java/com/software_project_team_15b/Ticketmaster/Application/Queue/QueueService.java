package com.software_project_team_15b.Ticketmaster.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Application-layer facade over the queue domain.
 *
 * <p>Owns the site-wide waiting queue and the set of admitted tokens. All token
 * validation and admission scheduling live here so that the domain service remains
 * free of auth dependencies. Per-event queue state, repository access, transactions
 * and retry policy live in the domain service.
 */
@Service
public class QueueService {
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.queue");

    private static final int MAX_VISITORS = 100;
    private static final int SITE_QUEUE_INTERVAL = 10;

    private final IAuth auth;
    private final IQueueDomainService queueDomainService;

    private final Queue<String> siteQueue = new LinkedList<>();
    private final Set<String> acceptedTokens = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public QueueService(IQueueDomainService queueDomainService, IAuth auth) {
        this.queueDomainService = Objects.requireNonNull(queueDomainService);
        this.auth = auth;
    }

    @PostConstruct
    private void startSiteQueueScheduler() {
        scheduler.scheduleAtFixedRate(this::acceptUsersFromSiteQueue, 0, SITE_QUEUE_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Evicts expired tokens from the admitted set, then drains the front of the
     * site queue into the admitted set until it reaches {@link #MAX_VISITORS}.
     * Tokens that have since expired while waiting in the queue are discarded.
     */
    private synchronized void acceptUsersFromSiteQueue() {
        acceptedTokens.removeIf(token -> !auth.isTokenValid(token));
        while (!siteQueue.isEmpty() && acceptedTokens.size() < MAX_VISITORS) {
            String token = siteQueue.poll();
            if (auth.isTokenValid(token)) {
                acceptedTokens.add(token);
            }
        }
    }

    private void validateToken(String token) {
        if (!auth.isTokenValid(token)) {
            AUDIT.warn("op=validateToken result=rejected reason=invalid_token");
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null or is already present in the queue
     */
    public synchronized void addUserToSiteQueue(String token) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=addUserToSiteQueue callerId={}", callerId);
        if (siteQueue.contains(token)) {
            throw new IllegalArgumentException("User is already in the site queue");
        }
        siteQueue.add(token);
        AUDIT.info("op=addUserToSiteQueue callerId={} result=ok", callerId);
    }

    /**
     * Returns {@code true} if the given token has been admitted from the site queue and
     * may proceed to access the website.
     *
     * @param token the user's auth token; must not be null
     * @return {@code true} if the token is currently in the admitted set
     * @throws IllegalArgumentException if {@code token} is null
     */
    public synchronized boolean validateAndExitQueue(String token) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=validateAndExitQueue callerId={}", callerId);
        boolean admitted = acceptedTokens.contains(token);
        AUDIT.info("op=validateAndExitQueue callerId={} admitted={}", callerId, admitted);
        return admitted;
    }

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors.
     *
     * @return {@code true} if the number of currently admitted users is below the site cap
     */
    public synchronized boolean canAccessWebsite() {
        AUDIT.info("op=canAccessWebsite");
        return acceptedTokens.size() < MAX_VISITORS;
    }

    /**
     * Returns the zero-based position of the given token in the event's waiting queue.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return the token's position (0 = next to be admitted)
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null,
     *                                  or if the token is not present in the queue
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public int getPositionInEventQueue(String token, UUID eventId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=getPositionInEventQueue callerId={} eventId={}", callerId, eventId);
        int position = queueDomainService.getPositionInEventQueue(token, eventId);
        AUDIT.info("op=getPositionInEventQueue callerId={} eventId={} position={}", callerId, eventId, position);
        return position;
    }

    /**
     * Returns {@code true} if the user identified by the given token is currently in the
     * admitted window for {@code eventId}.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     */
    public boolean isUserAdmitted(String token, UUID eventId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=isUserAdmitted callerId={} eventId={}", callerId, eventId);
        boolean admitted = queueDomainService.isUserAdmitted(token, eventId);
        AUDIT.info("op=isUserAdmitted callerId={} eventId={} admitted={}", callerId, eventId, admitted);
        return admitted;
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
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=getQueueAccessView callerId={} eventId={}", callerId, eventId);
        QueueAccessDTO view = queueDomainService.getQueueAccessView(token, eventId);
        AUDIT.info("op=getQueueAccessView callerId={} eventId={} status={}", callerId, eventId, view.status());
        return view;
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current access state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws QueueIsFullException     if the persistent queue has reached its capacity
     * @throws AlreadyInQueueException  if the user is already waiting in the queue
     */
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        if (!auth.isMember(token)) {
            AUDIT.warn("op=requestAccess callerId={} eventId={} result=rejected reason=non_member", callerId, eventId);
            throw new IllegalArgumentException("User must be a member to join the event queue");
        }
        AUDIT.info("op=requestAccess callerId={} eventId={}", callerId, eventId);
        QueueAccessDTO view = queueDomainService.requestAccess(token, eventId);
        AUDIT.info("op=requestAccess callerId={} eventId={} status={}", callerId, eventId, view.status());
        return view;
    }

    /**
     * Returns {@code true} if the user identified by the given token currently holds
     * admitted access to the event.
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted to the event
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null
     * @throws InvalidTokenException    if the token is invalid
     */
    public boolean hasAccess(String token, UUID eventId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=hasAccess callerId={} eventId={}", callerId, eventId);
        boolean access = queueDomainService.hasAccess(token, eventId);
        AUDIT.info("op=hasAccess callerId={} eventId={} result={}", callerId, eventId, access);
        return access;
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    public void createEventQueue(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=createEventQueue eventId={}", eventId);
        queueDomainService.createEventQueue(eventId);
        AUDIT.info("op=createEventQueue eventId={} result=ok", eventId);
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    public void deleteEventQueue(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=deleteEventQueue eventId={}", eventId);
        queueDomainService.deleteEventQueue(eventId);
        AUDIT.info("op=deleteEventQueue eventId={} result=ok", eventId);
    }

    /**
     * Removes and returns the user token at the front of the event's queue (FIFO order).
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the auth token of the user at the front of the queue
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws EmptyQueueException      if the queue contains no entries
     */
    public String popFromEventQueue(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        AUDIT.info("op=popFromEventQueue eventId={}", eventId);
        String token = queueDomainService.popFromEventQueue(eventId);
        AUDIT.info("op=popFromEventQueue eventId={} result=ok", eventId);
        return token;
    }

    /**
     * Appends the given user to the back of the event's queue.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the user's auth token; must not be null
     * @throws IllegalArgumentException  if {@code eventId} or {@code token} is null
     * @throws QueueNotFoundException    if no queue exists for the given event
     * @throws QueueIsFullException      if the queue has reached its capacity
     * @throws AlreadyInQueueException   if the token is already waiting in the queue
     */
    public void pushToEventQueue(UUID eventId, String token) {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        validateToken(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=pushToEventQueue callerId={} eventId={}", callerId, eventId);
        queueDomainService.pushToEventQueue(eventId, token);
        AUDIT.info("op=pushToEventQueue callerId={} eventId={} result=ok", callerId, eventId);
    }
}
