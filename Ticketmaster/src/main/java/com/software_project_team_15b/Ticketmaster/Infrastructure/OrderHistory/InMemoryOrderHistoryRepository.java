package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        if (orderHistory == null) {
            throw new IllegalArgumentException("orderHistory cannot be null");
        }

        UUID id = orderHistory.getOrderId();
        ReentrantLock lock = locks.computeIfAbsent(id, key -> new ReentrantLock());

        if (!lock.isHeldByCurrentThread()) {
            lock.lock();
        }

        try {
            storage.put(id, copyOf(orderHistory));
            return orderHistory;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }

            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                locks.remove(id, lock);
            }
        }
    }

    @Override
    public Optional<OrderHistory> findById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        // Return a defensive copy of the stored entity so callers can modify the
        // returned instance without mutating the repository until save() is called.
        OrderHistory stored = storage.get(orderId);
        if (stored == null) return Optional.empty();
        return Optional.of(copyOf(stored));
    }

    private OrderHistory copyOf(OrderHistory src) {
        OrderHistory copy = new OrderHistory(
                src.getOrderId(),
                src.getUserId(),
                src.getEventId(),
                src.getAreaId(),
                src.getPaymentTransactionId(),
                src.getTotalPrice(),
                src.getTickets()
        );
        if (src.isCancelled()) {
            copy.cancel();
        }
        return copy;
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

    @Override
    public List<OrderHistory> findByEventIdInAndIsCancelledFalse(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty())
            return List.of();
        return storage.values().stream()
                .filter(o -> eventIds.contains(o.getEventId()) && !o.isCancelled())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OrderHistory> findByIdForUpdate(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        ReentrantLock lock = locks.computeIfAbsent(orderId, key -> new ReentrantLock());
        lock.lock();

        OrderHistory stored = storage.get(orderId);

        if (stored == null) {
            lock.unlock();

            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                locks.remove(orderId, lock);
            }

            return Optional.empty();
        }

        if (stored.isCancelled()) {
            OrderHistory copy = copyOf(stored);

            lock.unlock();

            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                locks.remove(orderId, lock);
            }

            return Optional.of(copy);
        }

        return Optional.of(copyOf(stored));
    }
}