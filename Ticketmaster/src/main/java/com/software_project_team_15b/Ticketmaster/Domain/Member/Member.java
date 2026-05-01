package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.time.LocalDate;

@Entity
@Table(name = "members")
public class Member {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "member_id")
    private Set<Role> assignedRoles = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "active_role_id", nullable = true)
    private Role activeRole;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    protected Member() {
        // JPA only
    }

    public Member(String username, String passwordHash, Role initialRole, LocalDate birthDate) {
        validateUsername(username);
        validatePasswordHash(passwordHash);
        validateBirthDate(birthDate);

        this.userId = UUID.randomUUID();
        this.username = username.trim();
        this.passwordHash = passwordHash;
        this.birthDate = birthDate;
        if (initialRole != null) {
            this.assignedRoles.add(initialRole);
        }
        this.activeRole = initialRole; 
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

    public void setPassword(String passwordHash) {
        validatePasswordHash(passwordHash);
        this.passwordHash = passwordHash;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        validateBirthDate(birthDate);
        this.birthDate = birthDate;
    }

    public Role getActiveRole() {
        return activeRole;
    }

    public Set<Role> getAssignedRoles() {
        return Collections.unmodifiableSet(assignedRoles);
    }

    public void addRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

        assignedRoles.add(role);

        if (activeRole == null) {
            activeRole = role;
        }
    }

    public void removeRole(Role role) {
        if (role == null) {
            return;
        }
        assignedRoles.remove(role);
        if (role.equals(activeRole)) {
            activeRole = null;
        }
    }

    public void switchActiveRole(Role role) {
        if (role == null) {
            activeRole = null;
            return;
        }

        if (!assignedRoles.contains(role)) {
            throw new IllegalArgumentException("Cannot switch to a role that was not assigned to this member");
        }
        activeRole = role;
    }

    public void clearRoles() {
        assignedRoles.clear();
        activeRole = null;
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }

        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date cannot be in the future");
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