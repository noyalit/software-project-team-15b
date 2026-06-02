package com.software_project_team_15b.Ticketmaster.Infrastructure.Lottery;

import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaLotteryRepositoryAdapter implements ILotteryRepository {

    private final JpaLotterySpringDataRepository springDataRepository;

    @PersistenceContext
    private EntityManager em;

    public JpaLotteryRepositoryAdapter(JpaLotterySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Persists a new lottery atomically.
     *
     * <p>Uses {@link EntityManager#persist} (insert-only) so duplicate
     * {@code eventId}s surface as a single failure path — the previous
     * {@code existsById} + {@code save} version had a TOCTOU window where two
     * concurrent {@code addLottery} calls for the same event id could both
     * pass the existence check.</p>
     */
    @Override
    @Transactional
    public void addLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        try {
            em.persist(lottery);
            em.flush(); // surface duplicate-PK as DataIntegrityViolationException now, not at tx commit
        } catch (EntityExistsException | DataIntegrityViolationException e) {
            throw new IllegalArgumentException("A lottery with this event ID already exists", e);
        }
    }

    @Override
    public void removeLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        springDataRepository.deleteById(lottery.getEventId());
    }

    @Override
    public Lottery getLottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        return springDataRepository.findById(eventId).orElse(null);
    }

    @Override
    public void updateLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        springDataRepository.save(lottery);
    }
}
