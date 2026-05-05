package com.software_project_team_15b.Ticketmaster.Infrastructure.Lottery;

import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryLotteryRepository implements ILotteryRepository {

    private final Map<UUID, Lottery> store = new ConcurrentHashMap<>();

    @Override
    public void addLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        if (store.containsKey(lottery.getEventId())) {
            throw new IllegalArgumentException("A lottery with this event ID already exists");
        }
        store.put(lottery.getEventId(), lottery);
    }

    @Override
    public void removeLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        store.remove(lottery.getEventId());
    }

    @Override
    public Lottery getLottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        return store.get(eventId);
    }

    @Override
    public void updateLottery(Lottery lottery) {
        if (lottery == null) {
            throw new IllegalArgumentException("Lottery cannot be null");
        }
        store.put(lottery.getEventId(), lottery);
    }
}