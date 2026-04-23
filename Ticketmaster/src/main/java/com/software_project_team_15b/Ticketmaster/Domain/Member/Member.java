package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Member {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private final String userId;
    private String username;
    private String passwordHash;
    private Role role;

    public Member(String username, String rawPassword, Role role) {
        validateUsername(username);
        validatePassword(rawPassword);
        validateRole(role);

        this.userId = UUID.randomUUID().toString();
        this.username = username.trim();
        this.passwordHash = PASSWORD_ENCODER.encode(rawPassword);
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        validateUsername(username);
        this.username = username.trim();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPassword(String rawPassword) {
        validatePassword(rawPassword);
        this.passwordHash = PASSWORD_ENCODER.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword) {
        return rawPassword != null && PASSWORD_ENCODER.matches(rawPassword, this.passwordHash);
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        validateRole(role);
        this.role = role;
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        String regex = "^(?=.*[A-Z])(?=.*\\d).+$";
        if (!password.matches(regex)) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter and one number");
        }
    }

    private static void validateRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member member)) return false;
        return Objects.equals(userId, member.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}