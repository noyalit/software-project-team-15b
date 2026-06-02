package com.software_project_team_15b.Ticketmaster.Infrastructure.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaQueueRepositoryAdapter implements IQueueRepository {

    private final JpaQueueSpringDataRepository springDataRepository;

    public JpaQueueRepositoryAdapter(JpaQueueSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void addQueue(VirtualQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("queue cannot be null");
        }
        if (springDataRepository.existsById(queue.getId())) {
            throw new IllegalArgumentException("A queue with this ID already exists");
        }
        springDataRepository.save(queue);
    }

    @Override
    public void removeQueue(VirtualQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("queue cannot be null");
        }
        springDataRepository.deleteById(queue.getId());
    }

    @Override
    public VirtualQueue getQueue(UUID queueId) {
        if (queueId == null) {
            throw new IllegalArgumentException("queueId cannot be null");
        }
        return springDataRepository.findById(queueId).orElse(null);
    }

    @Override
    public void updateQueue(VirtualQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("queue cannot be null");
        }
        springDataRepository.save(queue);
    }

    @Override
    public List<VirtualQueue> getAllQueues() {
        return springDataRepository.findAll();
    }
}
