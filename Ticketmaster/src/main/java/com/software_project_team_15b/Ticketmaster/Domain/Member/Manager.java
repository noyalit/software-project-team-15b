package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.UUID;

@Entity
@DiscriminatorValue("MANAGER")
public class Manager extends Role {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.manager");

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "manager_permissions",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "permission")
    private Set<ManagerPermission> permissions = new HashSet<>();

    protected Manager() {
        // JPA only
    }

    public Manager(UUID appointedBy, UUID companyId, Set<ManagerPermission> permissions) {
        super(appointedBy, companyId);
        // A manager must always have at least one permission.
        setPermissions(permissions);
    }

    public Set<ManagerPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(ManagerPermission permission) {
        return permissions.contains(permission);
    }

    public void setPermissions(Set<ManagerPermission> permissions) {
        // Permissions are stored defensively to avoid shared mutable state.
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Manager must have at least one permission");
        }

        this.permissions.clear();
        this.permissions.addAll(permissions);

        AUDIT.info("op=set-manager-permissions roleId={} companyId={} permissions={}",
                getId(),
                getCompanyId(),
                this.permissions);
    }

    @Override
    public String getRoleName() {
        return "Manager";
    }
}