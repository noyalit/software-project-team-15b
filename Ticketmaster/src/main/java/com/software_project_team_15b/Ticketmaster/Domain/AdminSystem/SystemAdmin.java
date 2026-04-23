package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_admins")
public class SystemAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Column(name = "username", nullable = false, unique = true)
    private final String username;
    private String password;

    protected SystemAdmin() {
        this.username = null;
    }

    public SystemAdmin(String username, String password) {
        requireNonBlank(username, "username");
        validatePassword(password);
        this.username = username;
        this.password = password;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getUsername() {
        return username;
    }

    public void changePassword(String newPassword) {
        validatePassword(newPassword);
        this.password = newPassword;
    }

    public void assignAdminId(long adminId) {
        if (this.adminId != null) {
            return;
        }
        if (adminId <= 0) {
            throw new IllegalArgumentException("adminId must be positive");
        }
        this.adminId = adminId;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null/blank");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password cannot be null or empty");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters long");
        }

        String regex = "^(?=.*[A-Z])(?=.*\\d).+$";
        if (!password.matches(regex)) {
            throw new IllegalArgumentException("password must contain at least one uppercase letter and one number");
        }
    }
}
