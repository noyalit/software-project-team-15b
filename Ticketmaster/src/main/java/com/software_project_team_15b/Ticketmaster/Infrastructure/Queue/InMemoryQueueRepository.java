package com.software_project_team_15b.Ticketmaster.Infrastructure.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryQueueRepository implements IQueueRepository {

    private final Map<UUID, VirtualQueue> store = new ConcurrentHashMap<>();

    @Override
    public void addQueue(VirtualQueue queue) {
        store.put(queue.getId(), queue);
    }

    @Override
    public void removeQueue(VirtualQueue queue) {
        store.remove(queue.getId());
    }

    @Override
    public VirtualQueue getQueue(UUID queueId) {
        return store.get(queueId);
    }

    @Override
    public void updateQueue(VirtualQueue queue) {
        store.put(queue.getId(), queue);
    }
}