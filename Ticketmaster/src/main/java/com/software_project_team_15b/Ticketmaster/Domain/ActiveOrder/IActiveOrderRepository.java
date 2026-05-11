package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IActiveOrderRepository {

    /**
     * Saves the given active order.
     *
     * Implementations must enforce the uniqueness rule:
     * at most one ACTIVE order may exist for the same userId and eventId.
     *
     * JPA implementations should rely on the database constraint.
     * In-memory implementations should enforce the same rule manually.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         if saving would create two ACTIVE orders for the same user and event
     */
    ActiveOrder save(ActiveOrder order);

    ActiveOrder saveAndFlush(ActiveOrder order);

    Optional<ActiveOrder> findById(UUID id);

    List<ActiveOrder> findAll();

    void deleteById(UUID id);

    void delete(ActiveOrder order);

    void deleteAll(List<ActiveOrder> orders);

    List<ActiveOrder> findExpiredActiveOrdersForUpdate(ActiveOrderStatus status, LocalDateTime time);

    List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status);

    boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ActiveOrderStatus status);

    List<ActiveOrder> findByUserIdAndStatusForUpdate(UUID userId, ActiveOrderStatus status);

    List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status);

    Optional<ActiveOrder> findByIdForUpdate(UUID orderId);
}