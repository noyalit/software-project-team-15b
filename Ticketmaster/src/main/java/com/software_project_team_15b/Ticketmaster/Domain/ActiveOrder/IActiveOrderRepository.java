package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select ao
        from ActiveOrder ao
        where ao.status = :status
            and ao.expiresAt < :expiresBefore
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
        where ao.status <> :status
        """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    List<ActiveOrder> findByStatusNotForUpdate(ActiveOrderStatus status);
}