package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import java.util.*;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;

@Repository
@ConditionalOnProperty(
        name = "app.storage.mode",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryActiveOrderRepository implements IActiveOrderRepository {

    private final Map<UUID, ActiveOrder> storage = new ConcurrentHashMap<>();

    /*
     * In-memory approximation of DB pessimistic locking.
     *
     * Important:
     * This does not behave exactly like JPA PESSIMISTIC_WRITE,
     * because it does not hold the lock until transaction commit.
     *
     * It is good enough for memory mode / simple tests:
     * - repository writes are serialized
     * - "for update" reads are serialized
     * - multi-row results are sorted by orderId, like JPA queries with ORDER BY
     */
    private final Object lock = new Object();

    @Override
    public ActiveOrder save(ActiveOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }

        synchronized (lock) {
            if (order.getStatus() == ActiveOrderStatus.ACTIVE
                    && Boolean.TRUE.equals(order.getActiveUniquenessKey())) {

                boolean duplicateActiveOrderExists = storage.values().stream()
                        .anyMatch(existing ->
                                !existing.getOrderId().equals(order.getOrderId())
                                        && existing.getUserId().equals(order.getUserId())
                                        && existing.getEventId().equals(order.getEventId())
                                        && existing.getStatus() == ActiveOrderStatus.ACTIVE
                                        && Boolean.TRUE.equals(existing.getActiveUniquenessKey())
                        );

                if (duplicateActiveOrderExists) {
                    throw new org.springframework.dao.DataIntegrityViolationException(
                            "User already has an active order for this event"
                    );
                }
            }

            storage.put(order.getOrderId(), order);
            return order;
        }
    }

    @Override
    public ActiveOrder saveAndFlush(ActiveOrder order) {
        return save(order);
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        synchronized (lock) {
            return Optional.ofNullable(storage.get(orderId));
        }
    }

    @Override
    public List<ActiveOrder> findAll() {
        synchronized (lock) {
            return sortByOrderId(storage.values());
        }
    }

    @Override
    public void deleteById(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        synchronized (lock) {
            storage.remove(orderId);
        }
    }

    @Override
    public List<ActiveOrder> findExpiredActiveOrdersForUpdate(
            ActiveOrderStatus status,
            LocalDateTime time
    ) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (time == null) {
            throw new IllegalArgumentException("time cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .filter(order -> order.getStatus() == status)
                    .filter(order -> order.getExpiresAt() != null)
                    .filter(order -> order.getExpiresAt().isBefore(time))
                    .sorted(Comparator.comparing(ActiveOrder::getOrderId))
                    .toList();
        }
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatus(
            UUID userId,
            ActiveOrderStatus status
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .filter(order -> userId.equals(order.getUserId()))
                    .filter(order -> order.getStatus() == status)
                    .sorted(Comparator.comparing(ActiveOrder::getOrderId))
                    .toList();
        }
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatusForUpdate(
            UUID userId,
            ActiveOrderStatus status
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .filter(order -> userId.equals(order.getUserId()))
                    .filter(order -> order.getStatus() == status)
                    .sorted(Comparator.comparing(ActiveOrder::getOrderId))
                    .toList();
        }
    }

    @Override
    public List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .filter(order -> order.getStatus() != status)
                    .sorted(Comparator.comparing(ActiveOrder::getOrderId))
                    .toList();
        }
    }

    @Override
    public boolean existsByEventIdAndStatus(UUID eventId, ActiveOrderStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .anyMatch(order -> eventId.equals(order.getEventId()) && order.getStatus() == status);
        }
    }

    @Override
    public long countByEventIdAndStatus(UUID eventId, ActiveOrderStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        synchronized (lock) {
            return storage.values().stream()
                    .filter(order -> eventId.equals(order.getEventId()) && order.getStatus() == status)
                    .count();
        }
    }

    @Override
    public Optional<ActiveOrder> findByIdForUpdate(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        synchronized (lock) {
            return Optional.ofNullable(storage.get(orderId));
        }
    }

    @Override
    public void delete(ActiveOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }

        synchronized (lock) {
            storage.remove(order.getOrderId());
        }
    }

    @Override
    public void deleteAll(List<ActiveOrder> orders) {
        if (orders == null) {
            throw new IllegalArgumentException("orders cannot be null");
        }

        synchronized (lock) {
            for (ActiveOrder order : orders) {
                if (order != null) {
                    storage.remove(order.getOrderId());
                }
            }
        }
    }

    private List<ActiveOrder> sortByOrderId(Collection<ActiveOrder> orders) {
        return orders.stream()
                .sorted(Comparator.comparing(ActiveOrder::getOrderId))
                .toList();
    }
}