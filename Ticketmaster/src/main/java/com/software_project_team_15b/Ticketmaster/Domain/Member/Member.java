package com.software_project_team_15b.Ticketmaster.Domain.Member;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.RoleNotAssignedException;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@Table(name = "members")
public class Member {
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.member");

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
        // Invariants: username is non-blank; passwordHash is already-encoded; birthDate is not in the future.
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

        AUDIT.info("op=create-member userId={} username={} role={} birthDate={}",
                this.userId,
                this.username,
                this.activeRole == null ? null : this.activeRole.getRoleName(),
                this.birthDate);
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

        AUDIT.info("op=set-username userId={} username={}", this.userId, this.username);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPassword(String passwordHash) {
        // Note: passwordHash is expected to be already encoded by the application layer.
        validatePasswordHash(passwordHash);
        this.passwordHash = passwordHash;

        AUDIT.info("op=set-password-hash userId={}", this.userId);
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        validateBirthDate(birthDate);
        this.birthDate = birthDate;

        AUDIT.info("op=set-birth-date userId={} birthDate={}", this.userId, this.birthDate);
    }

    public Role getActiveRole() {
        return activeRole;
    }

    public Role getRole() {
        return activeRole;
    }

    public Set<Role> getAssignedRoles() {
        return Collections.unmodifiableSet(assignedRoles);
    }

    public void addRole(Role role) {
        if (role == null) {
            throw new InvalidMemberInputException("Role cannot be null");
        }

        assignedRoles.add(role);

        if (activeRole == null) {
            activeRole = role;
        }

        AUDIT.info("op=add-role userId={} role={} activeRole={}",
                this.userId,
                role.getRoleName(),
                this.activeRole == null ? null : this.activeRole.getRoleName());
    }

    public void removeRole(Role role) {
        if (role == null) {
            return;
        }
        assignedRoles.remove(role);
        if (role.equals(activeRole)) {
            activeRole = null;
        }

        AUDIT.info("op=remove-role userId={} role={} activeRole={}",
                this.userId,
                role.getRoleName(),
                this.activeRole == null ? null : this.activeRole.getRoleName());
    }

    public void switchActiveRole(Role role) {
        // Only roles already assigned to this member can become active.
        if (role == null) {
            activeRole = null;

            AUDIT.info("op=switch-active-role userId={} activeRole=null", this.userId);
            return;
        }

        if (!assignedRoles.contains(role)) {
            throw new RoleNotAssignedException("Cannot switch to a role that was not assigned to this member");
        }
        activeRole = role;

        AUDIT.info("op=switch-active-role userId={} activeRole={}", this.userId, role.getRoleName());
    }

    public void setRole(Role role) {
        // Convenience API: assigns the role (if not already assigned) and makes it active.
        if (role == null) {
            activeRole = null;

            AUDIT.info("op=set-role userId={} activeRole=null", this.userId);
            return;
        }

        assignedRoles.add(role);
        activeRole = role;

        AUDIT.info("op=set-role userId={} activeRole={}", this.userId, role.getRoleName());
    }

    public void clearRoles() {
        assignedRoles.clear();
        activeRole = null;

        AUDIT.info("op=clear-roles userId={}", this.userId);
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidMemberInputException("Username cannot be null or empty");
        }
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidMemberInputException("Password hash cannot be null or empty");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new InvalidMemberInputException("Birth date cannot be null");
        }

        if (birthDate.isAfter(LocalDate.now())) {
            throw new InvalidMemberInputException("Birth date cannot be in the future");
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