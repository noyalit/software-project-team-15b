package com.software_project_team_15b.Ticketmaster.Infrastructure.ActiveOrder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;

public interface JpaActiveOrderSpringDataRepository 
        extends JpaRepository<ActiveOrder, UUID> {

        List<ActiveOrder> findByStatusAndExpiresAtBefore(ActiveOrderStatus status, LocalDateTime time);
}