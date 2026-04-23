package com.software_project_team_15b.Ticketmaster.Infrastructure.AdminSystem;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemorySystemAdminRepository implements ISystemAdminRepository {

    private final ConcurrentHashMap<String, SystemAdmin> byId = new ConcurrentHashMap<>();

    @Override
    public SystemAdmin save(SystemAdmin systemAdmin) {
        if (systemAdmin == null) {
            throw new IllegalArgumentException("systemAdmin cannot be null");
        }
        byId.put(systemAdmin.getAdminId(), systemAdmin);
        return systemAdmin;
    }

    @Override
    public Optional<SystemAdmin> findById(String adminId) {
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
        for (SystemAdmin admin : byId.values()) {
            if (username.equals(admin.getUsername())) {
                return Optional.of(admin);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<SystemAdmin> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public void deleteById(String adminId) {
        if (adminId == null) {
            return;
        }
        byId.remove(adminId);
    }
}
