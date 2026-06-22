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

    /**
     * Creates a new member with the given credentials and an optional initial role.
     *
     * @param username     the desired username; must not be null or blank
     * @param passwordHash the pre-encoded password hash; must not be null or blank
     * @param initialRole  the role to assign and make active immediately, or {@code null} for a regular member
     * @param birthDate    the member's date of birth; must not be null or in the future
     * @throws InvalidMemberInputException if any validation constraint is violated
     */
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

    /**
     * Returns the unique identifier of this member.
     *
     * @return the member's {@link UUID}
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Returns the member's current username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Updates the member's username.
     *
     * @param username the new username; must not be null or blank
     * @throws InvalidMemberInputException if {@code username} is null or blank
     */
    public void setUsername(String username) {
        validateUsername(username);
        this.username = username.trim();

        AUDIT.info("op=set-username userId={} username={}", this.userId, this.username);
    }

    /**
     * Returns the member's stored password hash.
     *
     * @return the pre-encoded password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Replaces the member's password hash. The hash must already be encoded by the application layer.
     *
     * @param passwordHash the new pre-encoded password hash; must not be null or blank
     * @throws InvalidMemberInputException if {@code passwordHash} is null or blank
     */
    public void setPassword(String passwordHash) {
        validatePasswordHash(passwordHash);
        this.passwordHash = passwordHash;

        AUDIT.info("op=set-password-hash userId={}", this.userId);
    }

    /**
     * Returns the member's date of birth.
     *
     * @return the birth date
     */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /**
     * Updates the member's date of birth.
     *
     * @param birthDate the new birth date; must not be null or in the future
     * @throws InvalidMemberInputException if {@code birthDate} is null or in the future
     */
    public void setBirthDate(LocalDate birthDate) {
        validateBirthDate(birthDate);
        this.birthDate = birthDate;

        AUDIT.info("op=set-birth-date userId={} birthDate={}", this.userId, this.birthDate);
    }

    /**
     * Returns the member's currently active role, or {@code null} if they are a regular member.
     *
     * @return the active {@link Role}, or {@code null}
     */
    public Role getActiveRole() {
        return activeRole;
    }

    /**
     * Alias for {@link #getActiveRole()}.
     *
     * @return the active {@link Role}, or {@code null}
     */
    public Role getRole() {
        return activeRole;
    }

    /**
     * Returns an unmodifiable view of all roles currently assigned to this member.
     *
     * @return an unmodifiable {@link Set} of assigned {@link Role}s
     */
    public Set<Role> getAssignedRoles() {
        return Collections.unmodifiableSet(assignedRoles);
    }

    /**
     * Assigns a new role to this member. If the member currently has no active role, the new role
     * also becomes the active role.
     *
     * @param role the role to assign; must not be null
     * @throws InvalidMemberInputException if {@code role} is null
     */
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

    /**
     * Removes a role from this member's assigned roles. If the removed role was the active role,
     * the active role is promoted to an arbitrary surviving role, or set to {@code null} if no
     * roles remain.
     *
     * @param role the role to remove; no-op if {@code null}
     */
    public void removeRole(Role role) {
        if (role == null) {
            return;
        }
        assignedRoles.remove(role);
        if (role.equals(activeRole)) {
            activeRole = assignedRoles.isEmpty() ? null : assignedRoles.iterator().next();
        }

        AUDIT.info("op=remove-role userId={} role={} activeRole={}",
                this.userId,
                role.getRoleName(),
                this.activeRole == null ? null : this.activeRole.getRoleName());
    }

    /**
     * Changes the member's active role to the given role, which must already be assigned.
     * Passing {@code null} clears the active role, reverting the member to a regular member.
     *
     * @param role the role to activate, or {@code null} to clear the active role
     * @throws RoleNotAssignedException if {@code role} is non-null and not in the member's assigned roles
     */
    public void switchActiveRole(Role role) {
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

    /**
     * Convenience method that assigns the given role (if not already assigned) and immediately
     * makes it the active role. Passing {@code null} clears the active role.
     *
     * @param role the role to assign and activate, or {@code null} to clear the active role
     */
    public void setRole(Role role) {
        if (role == null) {
            activeRole = null;

            AUDIT.info("op=set-role userId={} activeRole=null", this.userId);
            return;
        }

        assignedRoles.add(role);
        activeRole = role;

        AUDIT.info("op=set-role userId={} activeRole={}", this.userId, role.getRoleName());
    }

    /**
     * Removes all assigned roles from this member and clears the active role.
     */
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
