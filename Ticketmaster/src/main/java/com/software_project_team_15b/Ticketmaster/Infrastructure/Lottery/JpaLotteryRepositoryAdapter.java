package com.software_project_team_15b.Ticketmaster.Infrastructure.Lottery;

import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaLotteryRepositoryAdapter implements ILotteryRepository {

    private final JpaLotterySpringDataRepository springDataRepository;

    public JpaLotteryRepositoryAdapter(JpaLotterySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void addLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        if (springDataRepository.existsById(lottery.getEventId())) {
            throw new IllegalArgumentException("A lottery with this event ID already exists");
        }
        springDataRepository.save(lottery);
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
