package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface JpaOrderHistorySpringDataRepository
        extends JpaRepository<OrderHistory, UUID> {

    List<OrderHistory> findByUserId(UUID userId);

    List<OrderHistory> findByEventId(UUID eventId);

    List<OrderHistory> findByEventIdAndIsCancelledFalse(UUID eventId);

    List<OrderHistory> findByEventIdIn(List<UUID> eventIds);

    List<OrderHistory> findByEventIdInAndIsCancelledFalse(List<UUID> eventIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderHistory o where o.orderId = :orderId")
    Optional<OrderHistory> findByIdForUpdate(@Param("orderId") UUID orderId);
}
