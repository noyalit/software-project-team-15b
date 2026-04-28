package com.software_project_team_15b.Ticketmaster.Infrastructure.AdminSystem;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaSystemAdminRepositoryAdapter implements ISystemAdminRepository {

    private final JpaSystemAdminSpringDataRepository springDataRepository;

    public JpaSystemAdminRepositoryAdapter(JpaSystemAdminSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public SystemAdmin save(SystemAdmin systemAdmin) {
        return springDataRepository.save(systemAdmin);
    }

    @Override
    public Optional<SystemAdmin> findById(UUID adminId) {
        return springDataRepository.findById(adminId);
    }

    @Override
    public Optional<SystemAdmin> findByUsername(String username) {
        return springDataRepository.findByUsername(username);
    }

    @Override
    public List<SystemAdmin> findAll() {
        return springDataRepository.findAll();
    }

    @Override
    public void deleteById(UUID adminId) {
        springDataRepository.deleteById(adminId);
    }
}
