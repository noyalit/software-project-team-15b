package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import java.util.*;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;

@Repository
@Primary
public class InMemoryActiveOrderRepository implements IActiveOrderRepository {

    private final Map<UUID, ActiveOrder> store = new HashMap<>();

    @Override
    public ActiveOrder save(ActiveOrder order) {
        store.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public List<ActiveOrder> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(UUID orderId) {
        store.remove(orderId);
    }

    @Override
    public List<ActiveOrder> findByStatusAndExpiresAtBefore(ActiveOrderStatus status, LocalDateTime time) {
        List<ActiveOrder> result = new ArrayList<>();
        for (ActiveOrder order : store.values()) {
            if (order.getStatus() == status && order.getExpiresAt().isBefore(time)) {
                result.add(order);
            }
        }
        return result;
    }
}