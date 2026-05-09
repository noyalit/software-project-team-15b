package com.software_project_team_15b.Ticketmaster.Application.Event.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ValidatePurchaseEligibilityIT {

    @Autowired
    EventManagementService service;

    @Test
    void passes_when_all_policies_are_satisfied() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), caller);

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 2, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(eventId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void throws_when_quantity_exceeds_max_tickets_policy() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), caller);

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 5, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(eventId, req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void throws_when_age_policy_rejects_minor() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), caller);

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(10), 1, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(eventId, req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void passes_when_event_has_no_purchase_policies() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                null, 100, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(eventId, req))
                .doesNotThrowAnyException();
    }
}
