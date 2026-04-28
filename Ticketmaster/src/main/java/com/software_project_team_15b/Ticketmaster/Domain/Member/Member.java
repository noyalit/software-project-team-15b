package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = false, orphanRemoval = true)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    protected Member() {
        // JPA only
    }

    public Member(String username, String rawPassword, Role role) {
        validateUsername(username);
        validatePassword(rawPassword);

        this.userId = UUID.randomUUID();
        this.username = username.trim();
        this.passwordHash = PASSWORD_ENCODER.encode(rawPassword);
        this.role = role;
    }

    @PrePersist
    protected void prePersist() {
        if (this.userId == null) {
            this.userId = UUID.randomUUID();
        }
    }

    public UUID getUserId() {
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