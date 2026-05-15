package com.software_project_team_15b.Ticketmaster.Application.Event.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.DTO.ConfirmationReceiptDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.HoldReceiptDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
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

    @Test
    void full_round_trip_create_publish_hold_confirm() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Rock Show", "The Band", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Arena", null, null), caller);

        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of("50.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null,
                List.of(
                        new AddAreaCommand.SeatSpec("A", "1"),
                        new AddAreaCommand.SeatSpec("A", "2"),
                        new AddAreaCommand.SeatSpec("A", "3")
                )), caller);

        service.publish(eventId, caller);

        List<UUID> seatIds = seatIdsIn(service.getEvent(eventId), areaId);
        UUID token = UUID.randomUUID();
        HoldReceiptDTO hold = service.hold(eventId,
                new HoldCommand(areaId, seatIds.subList(0, 2), null, token));
        assertThat(hold.quantity()).isEqualTo(2);

        ConfirmationReceiptDTO confirm = service.confirm(eventId, token);
        assertThat(confirm.quantity()).isEqualTo(2);
        EventDTO after = service.getEvent(eventId);
        assertThat(areaOf(after, areaId).availableCapacity()).isEqualTo(1);
    }

    @Test
    void cancel_prevents_confirm() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "Show", "X", Category.OTHER,
                Instant.now().plusSeconds(3600), "Hall", null, null), caller);
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Pit", Money.of("20.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"))), caller);
        service.publish(eventId, caller);

        List<UUID> seatIds = seatIdsIn(service.getEvent(eventId), areaId);
        UUID token = UUID.randomUUID();
        service.hold(eventId, new HoldCommand(areaId, seatIds, null, token));

        service.cancel(eventId, caller);

        assertThatThrownBy(() -> service.confirm(eventId, token))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void search_finds_event_by_name() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        service.createEvent(new CreateEventCommand(
                companyId, "Taylor Swift Eras Tour", "Taylor", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Stadium", null, null), caller);

        List<EventDTO> results = service.search(new SearchCriteria(
                "eras", null, null, null, null, null, null, null));

        assertThat(results).extracting(EventDTO::name).anyMatch(n -> n.toLowerCase().contains("eras"));
    }

    @Test
    void search_by_company_scopes_results() {
        UUID caller = UUID.randomUUID();
        UUID company1 = UUID.randomUUID();
        UUID company2 = UUID.randomUUID();
        service.createEvent(new CreateEventCommand(
                company1, "Company 1 Show", "A", Category.OTHER,
                Instant.now().plusSeconds(86400), "L", null, null), caller);
        service.createEvent(new CreateEventCommand(
                company2, "Company 2 Show", "B", Category.OTHER,
                Instant.now().plusSeconds(86400), "L", null, null), caller);

        List<EventDTO> c1 = service.searchInCompany(company1, SearchCriteria.empty());
        assertThat(c1).extracting(EventDTO::companyId).containsOnly(company1);
    }

    @Test
    void search_by_category_and_date() {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Instant target = Instant.now().plusSeconds(86400);
        service.createEvent(new CreateEventCommand(
                companyId, "Search Concert", "SC", Category.CONCERT,
                target, "Venue", null, null), caller);
        service.createEvent(new CreateEventCommand(
                companyId, "Search Sports", "SS", Category.SPORTS,
                target, "Venue", null, null), caller);

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
