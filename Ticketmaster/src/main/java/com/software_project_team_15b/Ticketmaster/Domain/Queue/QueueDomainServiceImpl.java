package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Domain service for managing virtual queues associated with events and the
 * site-wide waiting queue.
 *
 * <p>Owns the persistent {@link IQueueRepository} aggregate, the in-memory
 * site-queue and admitted-set, the per-event admission map, and the scheduler
 * that drains the site queue and expires per-event access windows. Each event
 * may have at most one queue, keyed by the event's UUID.
 *
 * <p>Mutating operations on persistent queues are transactional. Methods that
 * perform a read-then-write are additionally annotated with {@link Retryable} so that
 * transient optimistic-lock conflicts (arising when multiple threads update the same
 * aggregate concurrently) are transparently retried before propagating an error to the caller.
 */
@Service
public class QueueDomainServiceImpl implements IQueueDomainService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.queue");

    private final int ACCESS_TIME = 100;
    private final int MAX_VISITORS = 100;
    private final int SITE_QUEUE_INTERVAL = 10;

    private final IQueueRepository queueRepository;
    private final IAuth auth;
    // Self-reference through the Spring proxy so that @Retryable and @Transactional
    // on popFromEventQueue take effect when called from advanceEventQueue.
    @Autowired @Lazy private IQueueDomainService self;
    private final Queue<String> siteQueue = new LinkedList<>();
    private final Set<String> acceptedTokens = new HashSet<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LocalDateTime>> eventAccess = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public QueueDomainServiceImpl(IQueueRepository queueRepository, IAuth auth) {
        this.queueRepository = Objects.requireNonNull(queueRepository);
        this.auth = Objects.requireNonNull(auth);
    }

    /**
     * Starts the periodic site-queue advancement task on application startup.
     *
     * <p>Runs {@link #acceptUsersFromSiteQueue()} immediately and then once every
     * {@link #SITE_QUEUE_INTERVAL} seconds for the lifetime of the application.
     */
    @PostConstruct
    private void startSiteQueueScheduler() {
        scheduler.scheduleAtFixedRate(this::acceptUsersFromSiteQueue, 0, SITE_QUEUE_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Appends the given token to the back of the site-wide waiting queue.
     *
     * @param token the user's auth token; must not be null
     * @throws IllegalArgumentException if {@code token} is null or is already present in the queue
     */
    @Override
    public synchronized void addUserToSiteQueue(String token) {
        try {
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }
            if (siteQueue.contains(token)) {
                throw new IllegalArgumentException("User with token " + token + " is already in the site queue");
            }
            siteQueue.add(token);
            AUDIT.info("op=addUserToSiteQueue result=ok");
        } catch (RuntimeException e) {
            AUDIT.warn("op=addUserToSiteQueue result=rejected reason={}", e.getMessage());
            throw e;
        }
    }

    /**
     * Advances the site-wide queue by admitting waiting users up to {@link #MAX_VISITORS}.
     *
     * <p>First evicts any previously admitted tokens that are no longer valid (e.g. the
     * user's session expired), then drains the front of {@code siteQueue} into
     * {@code acceptedTokens} until either the queue is empty or the admitted set reaches
     * {@link #MAX_VISITORS}. Tokens in the queue that have since expired are skipped and
     * discarded. Called automatically by the scheduler; not intended for direct use.
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

    /**
     * Returns {@code true} if the given token has been admitted from the site queue and
     * may proceed to access the website.
     *
     * <p>A token is admitted once {@link #acceptUsersFromSiteQueue()} has moved it from
     * the waiting queue into the accepted set. Admitted tokens remain valid until the
     * underlying session expires, at which point the next scheduled run of
     * {@link #acceptUsersFromSiteQueue()} removes them automatically.
     *
     * @param token the user's auth token; must not be null
     * @return {@code true} if the token is currently in the admitted set
     * @throws IllegalArgumentException if {@code token} is null
     */
    @Override
    public boolean validateAndExitQueue(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        return acceptedTokens.contains(token);
    }

    /**
     * Returns {@code true} if the site currently has capacity for additional visitors.
     *
     * <p>This is a pure capacity signal — it does <em>not</em> admit the caller. A
     * controller may use this to decide whether to redirect an arriving user to the
     * queue page. To actually gain access the user must still call
     * {@link #addUserToSiteQueue(String)} so that their token enters the admitted set
     * via the scheduler; bypassing that step leaves the user untracked and unable to
     * pass {@link #validateAndExitQueue(String)}.
     *
     * @return {@code true} if the number of currently admitted users is below {@link #MAX_VISITORS}
     */
    @Override
    public boolean canAccessWebsite() {
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
    @Override
    public int getPositionInEventQueue(String token, UUID eventId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        VirtualQueue queue = queueRepository.getQueue(eventId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue with id " + eventId + " not found");
        }
        return queue.getPosition(token);
    }

    /**
     * Removes a user's timed access window for an event and immediately tries to fill
     * the vacated slot from the waiting queue.
     *
     * <p>Called automatically by the scheduler when a user's {@link #ACCESS_TIME}-second
     * window expires, and also by {@link #advanceEventQueue} indirectly. If the event
     * has been deleted (no access map present) the call is a no-op.
     *
     * @param userId  the unique identifier of the user whose access is expiring; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code userId} or {@code eventId} is null
     */
    protected synchronized void clearEventAccess(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        access.remove(userId);
        advanceEventQueue(eventId);
        AUDIT.info("op=clearEventAccess userId={} eventId={} result=ok", userId, eventId);
    }

    /**
     * Fills available admission slots for the given event by promoting users from its
     * persistent waiting queue.
     *
     * <p>Up to 100 users may hold simultaneous access to an event. Each promoted user is
     * granted a {@link #ACCESS_TIME}-second window; a callback is registered with the
     * scheduler to call {@link #clearEventAccess(UUID, UUID)} when that window closes.
     * Stops early if the queue is empty.
     *
     * <p>Uses {@code self} (the Spring proxy) to invoke {@link #popFromEventQueue} so
     * that {@link org.springframework.retry.annotation.Retryable} and
     * {@link org.springframework.transaction.annotation.Transactional} take effect.
     *
     * @param eventId the unique identifier of the event; must not be null
     */
    protected synchronized void advanceEventQueue(UUID eventId) {
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        if (access == null) return;
        while (access.size() < 100) {
            try {
                String nextToken = self.popFromEventQueue(eventId);
                UUID nextUser = auth.extractUserId(nextToken);
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ACCESS_TIME);
                access.put(nextUser, expiresAt);
                scheduler.schedule(() -> clearEventAccess(nextUser, eventId), ACCESS_TIME, TimeUnit.SECONDS);
            } catch (EmptyQueueException e) {
                break;
            }
        }
    }

    /**
     * Returns {@code true} if {@code userId} is currently in the admitted window for {@code eventId},
     * without performing any token validation.
     *
     * @param userId  the user to check; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return {@code true} if the user is currently admitted
     */
    @Override
    public boolean isUserAdmitted(UUID userId, UUID eventId) {
        ConcurrentHashMap<UUID, LocalDateTime> access = eventAccess.get(eventId);
        return access != null && access.containsKey(userId);
    }

    /**
     * Returns a snapshot of the user's current access state for the given event.
     *
     * <ul>
     *   <li>{@link QueueAccessStatus#NO_QUEUE} — the event has no virtual queue; the user
     *       may proceed directly.</li>
     *   <li>{@link QueueAccessStatus#ADMITTED} — the user has been admitted; the view
     *       includes the exact time at which access expires.</li>
     *   <li>{@link QueueAccessStatus#WAITING} — the user is still in the queue; the view
     *       includes their zero-based position.</li>
     * </ul>
     *
     * @param token   the user's auth token; must not be null
     * @param eventId the unique identifier of the event; must not be null
     * @return a {@link QueueAccessDTO} describing the user's current state
     * @throws IllegalArgumentException if {@code token} or {@code eventId} is null,
     *                                  or if the user is not present in the queue (when WAITING)
     * @throws InvalidTokenException    if the token is invalid
     * @throws QueueNotFoundException   if a queue exists for the event but cannot be read
     */
    @Override
    public QueueAccessDTO getQueueAccessView(String token, UUID eventId) {
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
        ConcurrentHashMap<UUID, LocalDateTime> admittedUsers = eventAccess.get(eventId);
        if (admittedUsers == null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.NO_QUEUE, null, null);
        }
        LocalDateTime expiresAt = admittedUsers.get(userId);
        if (expiresAt != null) {
            return new QueueAccessDTO(eventId, QueueAccessStatus.ADMITTED, null, expiresAt);
        }
        int position = getPositionInEventQueue(token, eventId);
        return new QueueAccessDTO(eventId, QueueAccessStatus.WAITING, position, null);
    }

    /**
     * Enters the user into the waiting queue for the given event and returns a snapshot
     * of their current access state.
     *
     * <p>If the user is already admitted (promoted from the queue and their window has not
     * yet expired), this method returns their current {@link QueueAccessDTO} immediately
     * without re-queuing them. Re-queuing an admitted user would corrupt state: because
     * the user was already popped from the persistent queue, {@code pushToEventQueue}
     * would not detect the duplicate and would silently add them a second time.
     *
     * <p>If the user is not yet enrolled they are appended to the back of the queue.
     * Depending on how many admission slots are currently free, the returned view will
     * have status {@link QueueAccessStatus#ADMITTED} (promoted immediately) or
     * {@link QueueAccessStatus#WAITING} (all 100 slots occupied).
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
    @Override
    public QueueAccessDTO requestAccess(String token, UUID eventId) {
        try {
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
            ConcurrentHashMap<UUID, LocalDateTime> admittedUsers = eventAccess.get(eventId);
            if (admittedUsers == null || !admittedUsers.containsKey(userId)) {
                self.pushToEventQueue(eventId, token);
            }
            QueueAccessDTO view = getQueueAccessView(token, eventId);
            AUDIT.info("op=requestAccess eventId={} userId={} result=ok", eventId, userId);
            return view;
        } catch (RuntimeException e) {
            AUDIT.warn("op=requestAccess eventId={} result=rejected reason={}", eventId, e.getMessage());
            throw e;
        }
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
    @Override
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
        return isUserAdmitted(userId, eventId);
    }

    /**
     * Creates a new, empty virtual queue for the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    @Override
    @Transactional
    public void createEventQueue(UUID eventId) {
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId cannot be null");
            }
            VirtualQueue queue = new VirtualQueue(eventId);
            queueRepository.addQueue(queue);
            eventAccess.put(eventId, new ConcurrentHashMap<>());
            advanceEventQueue(eventId);
            AUDIT.info("op=createEventQueue eventId={} result=ok", eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEventQueue eventId={} result=rejected reason={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the virtual queue associated with the given event.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     */
    @Override
    @Transactional
    public void deleteEventQueue(UUID eventId) {
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId cannot be null");
            }
            VirtualQueue queue = queueRepository.getQueue(eventId);
            if (queue == null) {
                throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
            }
            queueRepository.removeQueue(queue);
            eventAccess.remove(eventId);
            AUDIT.info("op=deleteEventQueue eventId={} result=ok", eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteEventQueue eventId={} result=rejected reason={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Removes and returns the user token at the front of the event's queue (FIFO order).
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @return the auth token of the user at the front of the queue
     * @throws IllegalArgumentException if {@code eventId} is null
     * @throws QueueNotFoundException   if no queue exists for the given event
     * @throws EmptyQueueException      if the queue contains no entries
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public String popFromEventQueue(UUID eventId) {
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId cannot be null");
            }
            VirtualQueue queue = queueRepository.getQueue(eventId);
            if (queue == null) {
                throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
            }
            if (queue.isEmpty()) {
                throw new EmptyQueueException("Event queue is empty (eventId: " + eventId + ")");
            }
            String value = queue.pop();
            queueRepository.updateQueue(queue);
            AUDIT.info("op=popFromEventQueue eventId={} result=ok", eventId);
            return value;
        } catch (RuntimeException e) {
            AUDIT.warn("op=popFromEventQueue eventId={} result=rejected reason={}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Appends the given user to the back of the event's queue.
     *
     * <p>If a concurrent update causes an optimistic-lock conflict the operation is
     * retried up to 3 times with a short backoff before the exception propagates.
     *
     * @param eventId the unique identifier of the event; must not be null
     * @param token   the user's auth token; must not be null
     * @throws IllegalArgumentException  if {@code eventId} or {@code token} is null
     * @throws QueueNotFoundException    if no queue exists for the given event
     * @throws QueueIsFullException      if the queue has reached its capacity
     * @throws AlreadyInQueueException   if the token is already waiting in the queue
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void pushToEventQueue(UUID eventId, String token) {
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId cannot be null");
            }
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }
            VirtualQueue queue = queueRepository.getQueue(eventId);
            if (queue == null) {
                throw new QueueNotFoundException("Queue not found for eventId: " + eventId);
            }
            if (queue.isFull()) {
                throw new QueueIsFullException("Event queue is full (eventId: " + eventId + ")");
            }
            if (queue.contains(token)) {
                throw new AlreadyInQueueException("Token " + token + " is already in the queue for eventId: " + eventId);
            }
            queue.push(token);
            queueRepository.updateQueue(queue);
            advanceEventQueue(eventId);
            AUDIT.info("op=pushToEventQueue eventId={} result=ok", eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=pushToEventQueue eventId={} result=rejected reason={}", eventId, e.getMessage());
            throw e;
        }
    }
}
