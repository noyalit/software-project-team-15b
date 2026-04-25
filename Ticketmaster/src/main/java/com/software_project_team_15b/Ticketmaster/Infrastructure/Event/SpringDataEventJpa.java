package com.software_project_team_15b.Ticketmaster.Infrastructure.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataEventJpa extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @Override
    @EntityGraph(attributePaths = {"areas"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Event> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e left join fetch e.areas where e.eventId = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
