package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOrderHistoryRepository {

    OrderHistory save(OrderHistory orderHistory);

    Optional<OrderHistory> findById(UUID orderId);

    List<OrderHistory> findAll();

    List<OrderHistory> findByUserId(UUID userId);

    List<OrderHistory> findByEventId(UUID eventId);

    List<OrderHistory> findByEventIdAndIsCancelledFalse(UUID eventId);

    List<OrderHistory> findByEventIdInAndIsCancelledFalse(List<UUID> eventIds);

    List<OrderHistory> findByEventIdIn(List<UUID> eventIds);

    Optional<OrderHistory> findByIdForUpdate(UUID orderId);
}