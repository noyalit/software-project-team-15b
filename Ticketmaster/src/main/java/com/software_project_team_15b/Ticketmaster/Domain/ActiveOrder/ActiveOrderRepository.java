package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;

@Repository
public interface ActiveOrderRepository extends JpaRepository<ActiveOrder, String> {
}