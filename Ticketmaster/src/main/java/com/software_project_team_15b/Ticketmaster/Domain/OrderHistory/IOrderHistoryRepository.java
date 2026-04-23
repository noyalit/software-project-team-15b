package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

public interface IOrderHistoryRepository extends JpaRepository<OrderHistory, String> {

    List<OrderHistory> findByUserId(String userId);

    List<OrderHistory> findByEventId(String eventId);

    List<OrderHistory> findByEventIdIn(List<String> eventIds);
}