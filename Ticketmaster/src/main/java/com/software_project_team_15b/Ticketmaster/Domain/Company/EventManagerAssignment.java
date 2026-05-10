package com.software_project_team_15b.Ticketmaster.Domain.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One row in the {@code company_managers} collection table: a single
 * (event, user) pair recording that {@code userId} is a manager of
 * {@code eventId} within the owning {@link Company}.
 *
 * <p>Modeled as an {@link Embeddable} so Hibernate can persist a flat
 * {@code Set<EventManagerAssignment>} via {@link jakarta.persistence.ElementCollection},
 * which is required because JPA cannot map a nested {@code Map<UUID, Set<UUID>>}
 * directly. The Map view is reconstructed in memory by
 * {@link Company#getEventManagers()}.
 */
@Embeddable
public class EventManagerAssignment {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected EventManagerAssignment() {
    }

    public EventManagerAssignment(UUID eventId, UUID userId) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventManagerAssignment other)) return false;
        return Objects.equals(eventId, other.eventId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId);
    }
}
