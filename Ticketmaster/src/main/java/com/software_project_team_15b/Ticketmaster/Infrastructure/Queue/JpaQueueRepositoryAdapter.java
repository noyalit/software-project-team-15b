package com.software_project_team_15b.Ticketmaster.Infrastructure.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaQueueRepositoryAdapter implements IQueueRepository {

    private final JpaQueueSpringDataRepository springDataRepository;

    @PersistenceContext
    private EntityManager em;

    public JpaQueueRepositoryAdapter(JpaQueueSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Persists a new virtual queue atomically.
     *
     * <p>Uses {@link EntityManager#persist} (insert-only) so duplicate queue
     * ids surface as a single failure path — the previous
     * {@code existsById} + {@code save} version had a TOCTOU window where two
     * concurrent {@code addQueue} calls for the same id could both pass the
     * existence check.</p>
     */
    @Override
    @Transactional
    public void addQueue(VirtualQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("queue cannot be null");
        }
        try {
            em.persist(queue);
            em.flush(); // surface duplicate-PK as DataIntegrityViolationException now, not at tx commit
        } catch (EntityExistsException | DataIntegrityViolationException e) {
            throw new IllegalArgumentException("A queue with this ID already exists", e);
        }
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
