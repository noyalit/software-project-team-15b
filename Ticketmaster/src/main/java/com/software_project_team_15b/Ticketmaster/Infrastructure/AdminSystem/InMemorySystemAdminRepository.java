package com.software_project_team_15b.Ticketmaster.Infrastructure.AdminSystem;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemorySystemAdminRepository implements ISystemAdminRepository {

    private static final Path SEQ_FILE = Path.of(
            System.getProperty("user.home"),
            ".ticketmaster",
            "system_admin_seq.txt"
    );

    private final AtomicLong sequence = new AtomicLong(loadInitialSequence());
    private final ConcurrentHashMap<Long, SystemAdmin> byId = new ConcurrentHashMap<>();

    @Override
    public SystemAdmin save(SystemAdmin systemAdmin) {
        if (systemAdmin == null) {
            throw new IllegalArgumentException("systemAdmin cannot be null");
        }
        if (systemAdmin.getAdminId() == null) {
            long next = nextId();
            systemAdmin.assignAdminId(next);
        }
        byId.put(systemAdmin.getAdminId(), systemAdmin);
        return systemAdmin;
    }

    @Override
    public Optional<SystemAdmin> findById(Long adminId) {
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
    public void deleteById(Long adminId) {
        if (adminId == null) {
            return;
        }
        byId.remove(adminId);
    }

    private long nextId() {
        long next = sequence.incrementAndGet();
        persistSequence(next);
        return next;
    }

    private static long loadInitialSequence() {
        try {
            if (!Files.exists(SEQ_FILE)) {
                return 0L;
            }
            String content = Files.readString(SEQ_FILE, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(content);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static void persistSequence(long value) {
        try {
            Files.createDirectories(SEQ_FILE.getParent());
            Files.writeString(
                    SEQ_FILE,
                    Long.toString(value),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }
}
