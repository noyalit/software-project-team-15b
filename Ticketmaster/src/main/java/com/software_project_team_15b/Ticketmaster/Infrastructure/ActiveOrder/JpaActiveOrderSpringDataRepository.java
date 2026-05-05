package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;

import jakarta.persistence.LockModeType;


public interface JpaActiveOrderSpringDataRepository 
        extends JpaRepository<ActiveOrder, UUID> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                SELECT a 
                FROM ActiveOrder a 
                WHERE a.status = :status 
                AND a.expiresAt < :time
                ORDER BY a.orderId
        """)
        List<ActiveOrder> findExpiredActiveOrdersForUpdate(
                @Param("status") ActiveOrderStatus status,
                @Param("time") LocalDateTime time
        );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                SELECT a
                FROM ActiveOrder a
                WHERE a.status <> :status
                ORDER BY a.orderId
        """)
        List<ActiveOrder> findByStatusNotForUpdate(
                @Param("status") ActiveOrderStatus status
        );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                SELECT a
                FROM ActiveOrder a
                WHERE a.orderId = :orderId
                ORDER BY a.orderId
        """)
        Optional<ActiveOrder> findByIdForUpdate(@Param("orderId") UUID orderId);

        List<ActiveOrder> findByUserIdAndStatus(UUID userId, ActiveOrderStatus status);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                SELECT a
                FROM ActiveOrder a
                WHERE a.userId = :userId
                AND a.status = :status
                ORDER BY a.orderId
        """)
        List<ActiveOrder> findByUserIdAndStatusForUpdate(
                @Param("userId") UUID userId,
                @Param("status") ActiveOrderStatus status
        );

        boolean existsByUserIdAndEventIdAndStatus(
        UUID userId,
        UUID eventId,
        ActiveOrderStatus status
        );
}