package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IOrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {

    List<OrderHistory> findByUserId(UUID userId);

    List<OrderHistory> findByEventId(UUID eventId);

    List<OrderHistory> findByEventIdIn(List<UUID> eventIds);
}