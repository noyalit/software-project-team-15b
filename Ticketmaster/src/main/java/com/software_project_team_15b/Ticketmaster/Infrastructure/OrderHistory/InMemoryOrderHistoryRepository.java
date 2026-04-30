package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryOrderHistoryRepository implements IOrderHistoryRepository {

    private final Map<UUID, OrderHistory> storage = new ConcurrentHashMap<>();

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        storage.put(orderHistory.getOrderId(), orderHistory);
        return orderHistory;
    }

    @Override
    public Optional<OrderHistory> findById(UUID orderId) {
        return Optional.ofNullable(storage.get(orderId));
    }

    @Override
    public List<OrderHistory> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<OrderHistory> findByUserId(UUID userId) {
        return storage.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByEventId(UUID eventId) {
        return storage.values().stream()
                .filter(o -> o.getEventId().equals(eventId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByEventIdIn(List<UUID> eventIds) {
        return storage.values().stream()
                .filter(o -> eventIds.contains(o.getEventId()))
                .collect(Collectors.toList());
    }
}