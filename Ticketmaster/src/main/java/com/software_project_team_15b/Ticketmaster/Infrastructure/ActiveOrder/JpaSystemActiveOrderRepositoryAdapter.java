package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Repository
@ConditionalOnProperty(
    name = "app.storage.mode",
    havingValue = "jpa"
)
public class JpaSystemActiveOrderRepositoryAdapter implements IActiveOrderRepository {
    
    private final JpaActiveOrderSpringDataRepository jpaActiveOrderSpringDataRepository;

    public JpaSystemActiveOrderRepositoryAdapter(JpaActiveOrderSpringDataRepository jpaActiveOrderSpringDataRepository) {
        this.jpaActiveOrderSpringDataRepository = jpaActiveOrderSpringDataRepository;
    }

    @Override
    public ActiveOrder save(ActiveOrder order) {
        if (order == null) 
            throw new IllegalArgumentException("order cannot be null");
        return jpaActiveOrderSpringDataRepository.save(order);
    }

    @Override
    public ActiveOrder saveAndFlush(ActiveOrder order) {
        if (order == null) 
            throw new IllegalArgumentException("order cannot be null");
        return jpaActiveOrderSpringDataRepository.saveAndFlush(order);
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {   
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        return jpaActiveOrderSpringDataRepository.findById(orderId);
    }

    @Override
    public List<ActiveOrder> findAll() {
        return jpaActiveOrderSpringDataRepository.findAll();
    }

    @Override
    public void deleteById(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");
        jpaActiveOrderSpringDataRepository.deleteById(orderId);
    }

    @Override
    public List<ActiveOrder> findExpiredActiveOrdersForUpdate(ActiveOrderStatus status, LocalDateTime time) {
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");
        if (time == null)
            throw new IllegalArgumentException("time cannot be null");
        return jpaActiveOrderSpringDataRepository.findExpiredActiveOrdersForUpdate(status, time);
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");

        return jpaActiveOrderSpringDataRepository.findByUserIdAndStatus(userId, status);
    }

    @Override
    public Optional<ActiveOrder> findByIdForUpdate(UUID orderId) {
        if (orderId == null)
            throw new IllegalArgumentException("orderId cannot be null");

        return jpaActiveOrderSpringDataRepository.findByIdForUpdate(orderId);
    }

    @Override
    public List<ActiveOrder> findByUserIdAndStatusForUpdate(UUID userId, ActiveOrderStatus status) {
        if (userId == null)
            throw new IllegalArgumentException("userId cannot be null");
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");

        return jpaActiveOrderSpringDataRepository.findByUserIdAndStatusForUpdate(userId, status);
    }

    @Override
    public List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status) {
        if (status == null)
            throw new IllegalArgumentException("status cannot be null");

        return jpaActiveOrderSpringDataRepository.findByStatusNotForUpdate(status);
    }

    @Override
    public void delete(ActiveOrder order) {
        if (order == null)
            throw new IllegalArgumentException("order cannot be null");
        jpaActiveOrderSpringDataRepository.delete(order);
    }

    @Override
    public void deleteAll(List<ActiveOrder> orders) {
        if (orders == null)
            throw new IllegalArgumentException("orders cannot be null");
        jpaActiveOrderSpringDataRepository.deleteAll(orders);
    }

}
