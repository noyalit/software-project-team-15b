package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

@Repository
public interface IActiveOrderRepository extends JpaRepository<ActiveOrder, String> {

    List<ActiveOrder> findByUserIdAndActiveOrderStatus(UUID userId, ActiveOrderStatus status);
}