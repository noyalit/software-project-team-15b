package com.software_project_team_15b.Ticketmaster.Infrastructure.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("select o from OrderHistory o where o.orderId = :id")
    Optional<OrderHistory> findByIdForUpdate(@Param("id") UUID id);
}
