package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import java.time.Instant;

/**
 * Context object passed into the Composite policy tree.
 *
 * <p>Bundles everything any leaf, condition, or composite might need to make a decision —
 * the buyer's request, the owning event and/or company (either may be null when not relevant
 * to the level being evaluated), and a snapshot of "now" so time-based conditions stay
 * testable and consistent within a single evaluation pass.
 */
public record PolicyContext(
        PurchaseRequest request,
        Event event,
        Company company,
        Instant now
) {
    public static PolicyContext of(PurchaseRequest request, Event event) {
        return new PolicyContext(request, event, null, Instant.now());
    }

    public static PolicyContext of(PurchaseRequest request, Company company) {
        return new PolicyContext(request, null, company, Instant.now());
    }

    public static PolicyContext of(PurchaseRequest request, Event event, Company company) {
        return new PolicyContext(request, event, company, Instant.now());
    }
}
