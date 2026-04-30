package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JpaSystemActiveOrderRepositoryAdapter implements IActiveOrderRepository {
    
    private final JpaActiveOrderSpringDataRepository jpaActiveOrderSpringDataRepository;

    public JpaSystemActiveOrderRepositoryAdapter(JpaActiveOrderSpringDataRepository jpaActiveOrderSpringDataRepository) {
        this.jpaActiveOrderSpringDataRepository = jpaActiveOrderSpringDataRepository;
    }

    @Override
    public ActiveOrder save(ActiveOrder order) {
        return jpaActiveOrderSpringDataRepository.save(order);
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {   
        return jpaActiveOrderSpringDataRepository.findById(orderId);
    }

    @Override
    public List<ActiveOrder> findAll() {
        return jpaActiveOrderSpringDataRepository.findAll();
    }

    @Override
    public void deleteById(UUID orderId) {
        jpaActiveOrderSpringDataRepository.deleteById(orderId);
    }

    @Override
    public List<ActiveOrder> findByStatusAndExpiresAtBefore(ActiveOrderStatus status, LocalDateTime time) {
        return jpaActiveOrderSpringDataRepository.findByStatusAndExpiresAtBefore(status, time);
    }

}
