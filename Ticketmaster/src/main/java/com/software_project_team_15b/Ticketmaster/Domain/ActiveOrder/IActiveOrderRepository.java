package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

@Repository
public interface IActiveOrderRepository extends JpaRepository<ActiveOrder, UUID> {

    List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ao from ActiveOrder ao where ao.orderId = :orderId")
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    Optional<ActiveOrder> findByIdForUpdate(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select ao
        from ActiveOrder ao
        where ao.userId = :userId
            and ao.status = :status
        """)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")
    })
    List<ActiveOrder> findByUserIdAndStatusForUpdate(
            UUID userId,
            ActiveOrderStatus status
    );

    boolean existsByUserIdAndEventIdAndStatus(
        UUID userId,
        UUID eventId,
        ActiveOrderStatus status
    );

}