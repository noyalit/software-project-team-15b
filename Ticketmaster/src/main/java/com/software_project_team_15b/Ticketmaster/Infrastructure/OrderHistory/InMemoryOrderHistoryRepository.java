package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.storage.mode",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryOrderHistoryRepository implements IOrderHistoryRepository {

    private final Map<UUID, OrderHistory> storage = new ConcurrentHashMap<>();

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        if (orderHistory == null)
            throw new IllegalArgumentException("orderHistory cannot be null");
        storage.put(orderHistory.getOrderId(), orderHistory);
        return orderHistory;
    }

    @Override
    public Optional<OrderHistory> findById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        return Optional.ofNullable(storage.get(orderId));
    }

    @Override
    public List<OrderHistory> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<OrderHistory> findByUserId(UUID userId) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        return storage.values().stream()
                .filter(o -> Objects.equals(o.getUserId(), userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByEventId(UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId cannot be null");
        return storage.values().stream()
                .filter(o -> Objects.equals(o.getEventId(), eventId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByEventIdAndIsCancelledFalse(UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId cannot be null");
        return storage.values().stream()
                .filter(o -> Objects.equals(o.getEventId(), eventId) && !o.isCancelled())
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByEventIdIn(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty())
            return List.of();
        return storage.values().stream()
                .filter(o -> eventIds.contains(o.getEventId()))
                .collect(Collectors.toList());
    }
}