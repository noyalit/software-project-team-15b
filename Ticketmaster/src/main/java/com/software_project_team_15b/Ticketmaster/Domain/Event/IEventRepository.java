package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IEventRepository {
    Optional<Event> findById(UUID id);
    Optional<Event> findByIdForUpdate(UUID id);
    Event save(Event event);
    List<Event> search(SearchCriteria criteria);
    List<Event> searchByCompany(UUID companyId, SearchCriteria criteria);
}
