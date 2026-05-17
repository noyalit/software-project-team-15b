package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class EventLockRegistry {

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock forEvent(UUID eventId) {
        return locks.computeIfAbsent(eventId, k -> new ReentrantLock());
    }

    public int size() { return locks.size(); }

    public void forget(UUID eventId) {
        ReentrantLock lock = locks.get(eventId);
        if (lock != null && !lock.isLocked()) {
            locks.remove(eventId, lock);
        }
    }
}
