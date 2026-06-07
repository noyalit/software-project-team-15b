package com.software_project_team_15b.Ticketmaster.Domain.Member;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;
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
    @Column(name = "permission", nullable = false)
    private Set<ManagerPermission> permissions = new HashSet<>();

    // Single-table inheritance: this column is shared with Founder/Owner rows,
    // which have no event, so it must be nullable at the DB level. A Manager's
    // event id is still required and enforced in code (see appointManager).
    @Column(name = "event_id", nullable = true)
    private UUID eventId;

    protected Manager() {
        // JPA only
    }

    public Manager(UUID appointedBy, UUID companyId, UUID eventId, Set<ManagerPermission> permissions) {
        super(appointedBy, companyId);
        this.eventId = eventId;
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
            throw new InvalidManagerPermissionsException("Manager must have at least one permission");
        }

        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    @Override
    public String getRoleName() {
        return "Manager";
    }
}