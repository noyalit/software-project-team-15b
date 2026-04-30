package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

@Repository
public interface IActiveOrderRepository extends JpaRepository<ActiveOrder, UUID> {

    List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select ao
           from ActiveOrder ao
           where ao.orderId = :orderId
           """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    Optional<ActiveOrder> findByIdForUpdate(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select ao
           from ActiveOrder ao
           where ao.status = :status
             and ao.expiresAt < :expiresBefore
           order by ao.orderId
           """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    List<ActiveOrder> findExpiredActiveOrdersForUpdate(
            ActiveOrderStatus status,
            LocalDateTime expiresBefore
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select ao
           from ActiveOrder ao
           where ao.userId = :userId
             and ao.status = :status
           order by ao.orderId
           """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    List<ActiveOrder> findByUserIdAndStatusForUpdate(
            UUID userId,
            ActiveOrderStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select ao
           from ActiveOrder ao
           where ao.status <> :status
           order by ao.orderId
           """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status);

    boolean existsByUserIdAndEventIdAndStatus(
            UUID userId,
            UUID eventId,
            ActiveOrderStatus status
    );
}