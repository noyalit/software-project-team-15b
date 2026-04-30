package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JpaOrderHistoryRepositoryAdapter implements IOrderHistoryRepository {

    private final JpaOrderHistorySpringDataRepository jpaRepo;

    public JpaOrderHistoryRepositoryAdapter(JpaOrderHistorySpringDataRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        return jpaRepo.save(orderHistory);
    }

    @Override
    public Optional<OrderHistory> findById(UUID orderId) {
        return jpaRepo.findById(orderId);
    }

    @Override
    public List<OrderHistory> findAll() {
        return jpaRepo.findAll();
    }

    @Override
    public List<OrderHistory> findByUserId(UUID userId) {
        return jpaRepo.findByUserId(userId);
    }

    @Override
    public List<OrderHistory> findByEventId(UUID eventId) {
        return jpaRepo.findByEventId(eventId);
    }

    @Override
    public List<OrderHistory> findByEventIdIn(List<UUID> eventIds) {
        return jpaRepo.findByEventIdIn(eventIds);
    }
}
