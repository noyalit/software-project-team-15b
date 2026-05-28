package com.software_project_team_15b.Ticketmaster.black.Application.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport.FounderActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventLifecycleIT {

    @Autowired
    EventManagementService service;

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IMemberRepository memberRepository;

    @Test
    void GivenDraftEventWithSeatingArea_WhenPublishHoldAndConfirm_ThenSeatsAreSoldAndAvailabilityDecreases() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);

        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Rock Show", "The Band", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Arena", null, null), actor.memberId());

        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of("50.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null,
                List.of(
                        new AddAreaCommand.SeatSpec("A", "1"),
                        new AddAreaCommand.SeatSpec("A", "2"),
                        new AddAreaCommand.SeatSpec("A", "3")
                )), actor.memberId());

        service.publish(eventId, actor.memberId());

        List<UUID> seatIds = seatIdsIn(service.getEvent(eventId), areaId);
        UUID token = UUID.randomUUID();
        HoldReceipt hold = eventDomainService.hold(eventId,
                new HoldCommand(areaId, seatIds.subList(0, 2), null, token));
        assertThat(hold.quantity()).isEqualTo(2);

        ConfirmationReceipt confirm = eventDomainService.confirm(eventId, token);
        assertThat(confirm.quantity()).isEqualTo(2);
        EventDTO after = service.getEvent(eventId);
        assertThat(areaOf(after, areaId).availableCapacity()).isEqualTo(1);
    }

    @Test
    void GivenHeldSeatsOnPublishedEvent_WhenEventIsCancelled_ThenConfirmFailsWithInvalidEventState() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "Show", "X", Category.OTHER,
                Instant.now().plusSeconds(3600), "Hall", null, null), actor.memberId());
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Pit", Money.of("20.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"))), actor.memberId());
        service.publish(eventId, actor.memberId());

        List<UUID> seatIds = seatIdsIn(service.getEvent(eventId), areaId);
        UUID token = UUID.randomUUID();
        eventDomainService.hold(eventId, new HoldCommand(areaId, seatIds, null, token));

        service.cancel(eventId, actor.memberId());

        assertThatThrownBy(() -> eventDomainService.confirm(eventId, token))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenEventWithMatchingName_WhenSearchByName_ThenReturnsMatchingEvent() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        service.createEvent(new CreateEventCommand(
                actor.companyId(), "Taylor Swift Eras Tour", "Taylor", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Stadium", null, null), actor.memberId());

        List<EventDTO> results = service.search(new SearchCriteria(
                "eras", null, null, null, null, null, null, null));

        assertThat(results).extracting(EventDTO::name).anyMatch(n -> n.toLowerCase().contains("eras"));
    }

    @Test
    void GivenEventsInTwoCompanies_WhenSearchInCompany_ThenOnlyReturnsThatCompanysEvents() {
        FounderActor founder1 = EventTestAuthSupport.newFounder(memberRepository);
        FounderActor founder2 = EventTestAuthSupport.newFounder(memberRepository);
        service.createEvent(new CreateEventCommand(
                founder1.companyId(), "Company 1 Show", "A", Category.OTHER,
                Instant.now().plusSeconds(86400), "L", null, null), founder1.memberId());
        service.createEvent(new CreateEventCommand(
                founder2.companyId(), "Company 2 Show", "B", Category.OTHER,
                Instant.now().plusSeconds(86400), "L", null, null), founder2.memberId());

        List<EventDTO> c1 = service.searchInCompany(founder1.companyId(), SearchCriteria.empty());
        assertThat(c1).extracting(EventDTO::companyId).containsOnly(founder1.companyId());
    }

    @Test
    void GivenEventsInTwoCategories_WhenSearchByCategoryAndDate_ThenReturnsOnlyMatchingCategory() {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        Instant target = Instant.now().plusSeconds(86400);
        service.createEvent(new CreateEventCommand(
                actor.companyId(), "Search Concert", "SC", Category.CONCERT,
                target, "Venue", null, null), actor.memberId());
        service.createEvent(new CreateEventCommand(
                actor.companyId(), "Search Sports", "SS", Category.SPORTS,
                target, "Venue", null, null), actor.memberId());

        List<EventDTO> results = service.search(new SearchCriteria(
                null, null, Category.CONCERT,
                target.minusSeconds(60), target.plusSeconds(60),
                null, null, null));
        assertThat(results).extracting(EventDTO::category).contains(Category.CONCERT);
        assertThat(results).extracting(EventDTO::category).doesNotContain(Category.SPORTS);
    }

    private static List<UUID> seatIdsIn(EventDTO view, UUID areaId) {
        return areaOf(view, areaId).seats().stream().map(EventDTO.SeatView::seatId).toList();
    }

    private static EventDTO.AreaView areaOf(EventDTO view, UUID areaId) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow();
    }
}
