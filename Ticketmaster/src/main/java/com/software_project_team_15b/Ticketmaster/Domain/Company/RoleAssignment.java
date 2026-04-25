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

    @ElementCollection(fetch = FetchType.EAGER)
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
        return permissions;
    }

    // ==============================================================================================================
    // Usecase methods

    public RoleAssignment(String memberId, CompanyRole role, String appointerId, Set<Permission> permissions) {
        this.memberId = memberId;
        this.role = role;
        this.appointerId = appointerId;
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }

    public void updatePermissions(Set<Permission> newPermissions) {
        this.permissions = newPermissions != null ? new HashSet<>(newPermissions) : new HashSet<>();
    }

    public void setAppointerId(String appointerId) {
        this.appointerId = appointerId;
    }
}