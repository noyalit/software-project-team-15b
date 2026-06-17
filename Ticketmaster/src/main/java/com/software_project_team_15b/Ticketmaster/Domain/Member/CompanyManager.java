package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

@Entity
@DiscriminatorValue("COMPANY_MANAGER")
public class CompanyManager extends Role {

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "company_manager_permissions",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "permission", nullable = false)
    private Set<ManagerPermission> permissions = new HashSet<>();

    protected CompanyManager() {
        // JPA only
    }

    public CompanyManager(UUID appointedBy, UUID companyId, Set<ManagerPermission> permissions) {
        super(appointedBy, companyId);
        setPermissions(permissions);
    }

    public Set<ManagerPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(ManagerPermission permission) {
        return permissions.contains(permission);
    }

    public void setPermissions(Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new InvalidManagerPermissionsException("Company manager must have at least one permission");
        }

        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    @Override
    public String getRoleName() {
        return "CompanyManager";
    }
}