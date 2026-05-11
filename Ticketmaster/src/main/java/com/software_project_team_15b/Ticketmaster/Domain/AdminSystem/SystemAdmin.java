package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "system_admins")
public class SystemAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "username", nullable = false, unique = true)
    private final String username;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected SystemAdmin() {
        this.username = null;
    }

    public SystemAdmin(String username, String passwordHash) {
        requireNonBlank(username, "username");
        validatePasswordHash(passwordHash);
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getUsername() {
        return username;
    }


    public void setPassword(String passwordHash) {
        validatePasswordHash(passwordHash);
        this.passwordHash = passwordHash;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void assignAdminId(UUID adminId) {
        if (this.adminId != null) {
            return;
        }
        if (adminId == null) {
            throw new IllegalArgumentException("adminId cannot be null");
        }
        this.adminId = adminId;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null/blank");
        }
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
    }
}
