package com.software_project_team_15b.Ticketmaster.Infrastructure.AdminSystem;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaSystemAdminSpringDataRepository extends JpaRepository<SystemAdmin, Long> {

    Optional<SystemAdmin> findByUsername(String username);
}
