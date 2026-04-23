package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ISystemAdminRepository extends JpaRepository<SystemAdmin, String> {

    Optional<SystemAdmin> findByUsername(String username);
}
