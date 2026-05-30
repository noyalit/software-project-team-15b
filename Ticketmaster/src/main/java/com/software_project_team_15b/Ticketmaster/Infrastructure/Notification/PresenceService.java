package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PresenceService {

    private final ConcurrentMap<UUID, Integer> userConnectionCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<UUID>> sessionToUserIds = new ConcurrentHashMap<>();

    public boolean isOnline(UUID userId) {
        Integer c = userConnectionCounts.get(userId);
        return c != null && c > 0;
    }

    public void registerSubscription(String sessionId, UUID userId) {
        sessionToUserIds.compute(sessionId, (sid, set) -> {
            if (set == null) set = ConcurrentHashMap.newKeySet();
            set.add(userId);
            return set;
        });

        userConnectionCounts.merge(userId, 1, Integer::sum);
    }

    public void unregisterSession(String sessionId) {
        Set<UUID> set = sessionToUserIds.remove(sessionId);
        if (set == null) return;

        for (UUID userId : set) {
            userConnectionCounts.computeIfPresent(userId, (k, v) -> (v <= 1) ? null : v - 1);
        }
    }
}
