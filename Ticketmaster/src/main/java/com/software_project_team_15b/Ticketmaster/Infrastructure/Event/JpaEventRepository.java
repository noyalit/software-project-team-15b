package com.software_project_team_15b.Ticketmaster.Infrastructure.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!pessimistic")
public class JpaEventRepository implements IEventRepository {

    private final SpringDataEventJpa jpa;

    public JpaEventRepository(SpringDataEventJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Event> findByIdForUpdate(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Event save(Event event) {
        return jpa.save(event);
    }

    @Override
    public List<Event> search(SearchCriteria criteria) {
        return jpa.findAll(EventSearchSpecification.matching(criteria));
    }

    @Override
    public List<Event> searchByCompany(UUID companyId, SearchCriteria criteria) {
        Specification<Event> spec = EventSearchSpecification.forCompany(companyId)
                .and(EventSearchSpecification.matching(criteria));
        return jpa.findAll(spec);
    }
}
