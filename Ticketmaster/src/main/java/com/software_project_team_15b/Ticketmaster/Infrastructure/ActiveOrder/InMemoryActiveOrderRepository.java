package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import java.util.*;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;


@Profile("memory")
@Repository
public class InMemoryActiveOrderRepository implements IActiveOrderRepository {

    private final Map<UUID, ActiveOrder> store = new ConcurrentHashMap<>();

    @Override
    public ActiveOrder save(ActiveOrder order) {
        if (order == null) 
            throw new IllegalArgumentException("order cannot be null");
        store.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public List<ActiveOrder> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        store.remove(orderId);
    }

    @Override
    public List<ActiveOrder> findExpiredActiveOrdersForUpdate(ActiveOrderStatus status, LocalDateTime time) {
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");
        if (time == null)
            throw new IllegalArgumentException("time cannot be null");
        List<ActiveOrder> result = new ArrayList<>();
        for (ActiveOrder order : store.values()) {
            if (order.getStatus() == status && order.getExpiresAt().isBefore(time)) {
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");
        List<ActiveOrder> result = new ArrayList<>();
        for (ActiveOrder order : store.values()) {
            if (order.getUserId().equals(userId) && order.getStatus() == status) {
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ActiveOrderStatus status) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        if (eventId == null)
            throw new IllegalArgumentException("eventId cannot be null");
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");
        for (ActiveOrder order : store.values()) {
            if (order.getUserId().equals(userId) && order.getEventId().equals(eventId) && order.getStatus() == status) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatusForUpdate(UUID userId, ActiveOrderStatus status) {
        return findByUserIdAndStatus(userId, status);
    }

    @Override
    public List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status) {
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");
        List<ActiveOrder> result = new ArrayList<>();
        for (ActiveOrder order : store.values()) {
            if (order.getStatus() != status) {
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public Optional<ActiveOrder> findByIdForUpdate(UUID orderId) {
        return findById(orderId);
    }
}