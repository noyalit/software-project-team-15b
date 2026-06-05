package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaOrderHistoryRepositoryAdapter implements IOrderHistoryRepository {

    private final JpaOrderHistorySpringDataRepository jpaRepo;

    public JpaOrderHistoryRepositoryAdapter(JpaOrderHistorySpringDataRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        if (orderHistory == null)
            throw new IllegalArgumentException("orderHistory cannot be null");
        return jpaRepo.save(orderHistory);
    }

    @Override
    public Optional<OrderHistory> findById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        return jpaRepo.findById(orderId);
    }

    @Override
    public List<OrderHistory> findAll() {
        return jpaRepo.findAll();
    }

    @Override
    public List<OrderHistory> findByUserId(UUID userId) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        return jpaRepo.findByUserId(userId);
    }

    @Override
    public List<OrderHistory> findByEventId(UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId cannot be null");
        return jpaRepo.findByEventId(eventId);
    }

    @Override
    public List<OrderHistory> findByEventIdAndIsCancelledFalse(UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId cannot be null");
        return jpaRepo.findByEventIdAndIsCancelledFalse(eventId);
    }

    @Override
    public List<OrderHistory> findByEventIdIn(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty())
            return List.of();
        return jpaRepo.findByEventIdIn(eventIds);
    }

    @Override
    public List<OrderHistory> findByEventIdInAndIsCancelledFalse(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty())
            return List.of();
        return jpaRepo.findByEventIdInAndIsCancelledFalse(eventIds);
    }

    @Override
    public Optional<OrderHistory> findByIdForUpdate(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        return jpaRepo.findByIdForUpdate(orderId);
    }
    
}
