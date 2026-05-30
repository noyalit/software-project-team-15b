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
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport.FounderActor;
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

    @Autowired
    IMemberRepository memberRepository;

    @Test
    void GivenMaxTicketsAndAgePolicies_WhenRequestMeetsBoth_ThenDoesNotThrow() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), actor.memberId());

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 2, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(eventId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenMaxTicketsPolicy_WhenRequestQuantityExceedsLimit_ThenThrowsPolicyViolation() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), actor.memberId());

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(30), 5, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(eventId, req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void GivenAgeRestrictionPolicy_WhenBuyerIsMinor_ThenThrowsPolicyViolation() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        List<IEventPurchasePolicy> policies = List.of(
                new MaxTicketsPerOrderPolicy(4),
                new AgeRestrictionPolicy(18)
        );
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", policies, null), actor.memberId());

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().minusYears(10), 1, List.of(), null);

        assertThatThrownBy(() -> service.validatePurchaseEligibility(eventId, req))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void GivenEventWithoutPurchasePolicies_WhenValidate_ThenDoesNotThrow() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Show", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), actor.memberId());

        PurchaseRequest req = new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                null, 100, List.of(), null);

        assertThatCode(() -> service.validatePurchaseEligibility(eventId, req))
                .doesNotThrowAnyException();
    }
}
