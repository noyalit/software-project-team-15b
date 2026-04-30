package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    public Manager(UUID appointedBy, Set<ManagerPermission> permissions) {
        super(appointedBy);
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
            throw new IllegalArgumentException("Manager must have at least one permission");
        }

        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    @Override
    public String getRoleName() {
        return "Manager";
    }
}