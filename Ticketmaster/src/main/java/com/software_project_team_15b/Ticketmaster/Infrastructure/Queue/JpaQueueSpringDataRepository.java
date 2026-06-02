package com.software_project_team_15b.Ticketmaster.Infrastructure.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaQueueSpringDataRepository extends JpaRepository<VirtualQueue, UUID> {
}
