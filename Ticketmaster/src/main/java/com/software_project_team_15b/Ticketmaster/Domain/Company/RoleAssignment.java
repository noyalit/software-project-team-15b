package com.software_project_team_15b.Ticketmaster.Domain.Company;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "company_roles")
public class RoleAssignment {

    // ==============================================================================================================
    // Fields

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyRole role;

    @Column(nullable = true)
    private String appointerId; // Null for Founder

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<Permission> permissions; // Empty for Owners/Founders

    protected RoleAssignment() {
    }

    // ==============================================================================================================
    // Getters

    public String getMemberId() {
        return memberId;
    }

    public CompanyRole getRole() {
        return role;
    }

    public String getAppointerId() {
        return appointerId;
    }

    public Set<Permission> getPermissions() {
        return java.util.Collections.unmodifiableSet(permissions);
    }

    // ==============================================================================================================
    // Usecase methods

    public RoleAssignment(String memberId, CompanyRole role, String appointerId, Set<Permission> permissions) {
        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("memberId cannot be null or empty");
        }
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }
        if (role == CompanyRole.FOUNDER && appointerId != null) {
            throw new IllegalArgumentException("A Founder cannot have an appointer");
        }
        if (role == CompanyRole.MANAGER && (permissions == null || permissions.isEmpty())) {
            throw new IllegalArgumentException("A Manager must have at least one permission assigned");
        }

        this.memberId = memberId;
        this.role = role;
        this.appointerId = appointerId;
        this.permissions = role == CompanyRole.MANAGER ? new HashSet<>(permissions) : new HashSet<>();
    }

    public void updatePermissions(Set<Permission> newPermissions) {
        if (this.role != CompanyRole.MANAGER) {
            throw new IllegalStateException("Only managers can have specific permissions updated.");
        }
        if (newPermissions == null || newPermissions.isEmpty()) {
            throw new IllegalArgumentException("A Manager must have at least one permission assigned");
        }
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
    }

    public void setAppointerId(String appointerId) {
        this.appointerId = appointerId;
    }
}