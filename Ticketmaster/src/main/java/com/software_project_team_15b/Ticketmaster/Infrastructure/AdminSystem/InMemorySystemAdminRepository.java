package com.software_project_team_15b.Ticketmaster.Infrastructure.AdminSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemorySystemAdminRepository implements ISystemAdminRepository {

    private final Map<UUID, SystemAdmin> byId = new ConcurrentHashMap<>();

    @Override
    public SystemAdmin save(SystemAdmin systemAdmin) {
        if (systemAdmin == null) {
            throw new IllegalArgumentException("systemAdmin cannot be null");
        }

        if (systemAdmin.getAdminId() == null) {
            systemAdmin.assignAdminId(UUID.randomUUID());
        }

        byId.put(systemAdmin.getAdminId(), systemAdmin);
        return systemAdmin;
    }

    @Override
    public Optional<SystemAdmin> findById(UUID adminId) {
        if (adminId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(adminId));
    }

    @Override
    public Optional<SystemAdmin> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }

        return byId.values().stream()
                .filter(admin -> username.equals(admin.getUsername()))
                .findFirst();
    }

    @Override
    public List<SystemAdmin> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public void deleteById(UUID adminId) {
        if (adminId == null) {
            return;
        }
        byId.remove(adminId);
    }

    @Override
    public void deleteAll() {
        byId.clear();
    }
}