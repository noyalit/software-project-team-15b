package com.software_project_team_15b.Ticketmaster.Infrastructure.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class EventSearchSpecification {

    private EventSearchSpecification() {}

    public static Specification<Event> matching(SearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.name() != null && !c.name().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + c.name().toLowerCase() + "%"));
            }
            if (c.artist() != null && !c.artist().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("artist")), "%" + c.artist().toLowerCase() + "%"));
            }
            if (c.category() != null) {
                predicates.add(cb.equal(root.get("category"), c.category()));
            }
            if (c.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), c.dateFrom()));
            }
            if (c.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), c.dateTo()));
            }
            if (c.location() != null && !c.location().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + c.location().toLowerCase() + "%"));
            }
            if (c.priceMin() != null || c.priceMax() != null) {
                Join<Event, EventArea> areas = root.join("areas");
                if (c.priceMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(areas.<Money>get("basePrice").<java.math.BigDecimal>get("amount"), c.priceMin()));
                }
                if (c.priceMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(areas.<Money>get("basePrice").<java.math.BigDecimal>get("amount"), c.priceMax()));
                }
                query.distinct(true);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Event> forCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }
}
