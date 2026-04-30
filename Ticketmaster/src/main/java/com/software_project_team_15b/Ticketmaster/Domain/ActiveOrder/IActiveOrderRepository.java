package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IActiveOrderRepository {

    ActiveOrder save(ActiveOrder order);

    Optional<ActiveOrder> findById(UUID id);

    List<ActiveOrder> findAll();

    void deleteById(UUID id);

    List<ActiveOrder> findByStatusAndExpiresAtBefore(ActiveOrderStatus status, LocalDateTime time);
}