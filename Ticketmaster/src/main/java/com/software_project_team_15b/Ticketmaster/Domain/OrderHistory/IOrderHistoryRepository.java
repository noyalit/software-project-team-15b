package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IOrderHistoryRepository extends JpaRepository<OrderHistory, String> {

    List<OrderHistory> findByUserId(String userId);

    List<OrderHistory> findByEventId(String eventId);

    List<OrderHistory> findByEventIds(List<String> eventIds);
}