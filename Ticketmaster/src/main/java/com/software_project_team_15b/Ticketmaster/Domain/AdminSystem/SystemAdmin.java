package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

public class SystemAdmin {

    private final String adminId;
    private final String username;
    private String password;

    public SystemAdmin(String adminId, String username, String password) {
        requireNonBlank(adminId, "adminId");
        requireNonBlank(username, "username");
        requireNonBlank(password, "password");
        this.adminId = adminId;
        this.username = username;
        this.password = password;
    }

    public String getAdminId() {
        return adminId;
    }

    public String getUsername() {
        return username;
    }

    public void changePassword(String newPassword) {
        requireNonBlank(newPassword, "newPassword");
        this.password = newPassword;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null/blank");
        }
    }
}
