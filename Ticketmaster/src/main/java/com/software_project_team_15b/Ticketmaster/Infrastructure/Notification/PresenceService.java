package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks which users currently have at least one open WebSocket connection.
 *
 * <p>The notifier consults this service to decide whether a notification can be
 * pushed live ({@link #isOnline(UUID)}) or must instead be persisted for later
 * delivery. Presence is reference-counted per user because a single user may have
 * several concurrent sessions (multiple tabs or devices); the user is considered
 * online until the last of their sessions disconnects.</p>
 *
 * <p>All state is held in concurrent maps so the class is safe to use from the
 * multiple threads that handle WebSocket lifecycle events.</p>
 */
@Service
public class PresenceService {

    /** Number of live connections per user; absence or {@code 0} means offline. */
    private final ConcurrentMap<UUID, Integer> userConnectionCounts = new ConcurrentHashMap<>();

    /** Users associated with each WebSocket session, used to decrement counts on disconnect. */
    private final ConcurrentMap<String, Set<UUID>> sessionToUserIds = new ConcurrentHashMap<>();

    /**
     * Returns whether the given user currently has any open connection.
     *
     * @param userId identifier of the user to check
     * @return {@code true} if at least one session is registered for the user
     */
    public boolean isOnline(UUID userId) {
        Integer c = userConnectionCounts.get(userId);
        return c != null && c > 0;
    }

    /**
     * Records that a session has subscribed on behalf of a user, marking the user online.
     *
     * <p>Associates {@code userId} with {@code sessionId} and increments the user's
     * connection count. Safe to call once per user/session subscription.</p>
     *
     * @param sessionId the WebSocket session identifier
     * @param userId    identifier of the user the session belongs to
     */
    public void registerSubscription(String sessionId, UUID userId) {
        sessionToUserIds.compute(sessionId, (sid, set) -> {
            if (set == null) set = ConcurrentHashMap.newKeySet();
            set.add(userId);
            return set;
        });

        userConnectionCounts.merge(userId, 1, Integer::sum);
    }

    /**
     * Removes a disconnected session and decrements the connection count of every
     * user that was associated with it, marking a user offline once their last
     * session is gone.
     *
     * <p>No-op if the session was never registered.</p>
     *
     * @param sessionId the WebSocket session identifier that disconnected
     */
    public void unregisterSession(String sessionId) {
        Set<UUID> set = sessionToUserIds.remove(sessionId);
        if (set == null) return;

        for (UUID userId : set) {
            userConnectionCounts.computeIfPresent(userId, (k, v) -> (v <= 1) ? null : v - 1);
        }
    }
}
