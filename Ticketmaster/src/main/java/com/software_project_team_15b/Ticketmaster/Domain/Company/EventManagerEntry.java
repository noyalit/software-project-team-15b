package com.software_project_team_15b.Ticketmaster.Domain.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.UUID;

/**
 * Embeddable pair that records a single (event, manager) assignment.
 *
 * <p>Stored as one row in {@code company_event_managers}. The combination
 * of {@code eventId} and {@code managerId} is unique within a company.
 */
@Embeddable
public class EventManagerEntry {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    protected EventManagerEntry() {
    }

    public EventManagerEntry(UUID eventId, UUID managerId) {
        this.eventId = eventId;
        this.managerId = managerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventManagerEntry other)) return false;
        return Objects.equals(eventId, other.eventId) && Objects.equals(managerId, other.managerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, managerId);
    }
}
