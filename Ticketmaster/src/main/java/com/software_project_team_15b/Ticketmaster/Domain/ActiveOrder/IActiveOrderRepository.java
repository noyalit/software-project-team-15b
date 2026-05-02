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

    List<ActiveOrder> findExpiredActiveOrdersForUpdate(ActiveOrderStatus status, LocalDateTime time);

    List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status);

    boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ActiveOrderStatus status);

    List<ActiveOrder> findByUserIdAndStatusForUpdate(UUID userId, ActiveOrderStatus status);

    List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status);

    Optional<ActiveOrder> findByIdForUpdate(UUID orderId);
}