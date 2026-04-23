package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

import java.util.List;
import java.util.Optional;

public interface ISystemAdminRepository {

    SystemAdmin save(SystemAdmin systemAdmin);

    Optional<SystemAdmin> findById(String adminId);

    Optional<SystemAdmin> findByUsername(String username);

    List<SystemAdmin> findAll();

    void deleteById(String adminId);
}
