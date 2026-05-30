package com.software_project_team_15b.Ticketmaster.black.Application.Event.concurrency;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport;
import com.software_project_team_15b.Ticketmaster.black.Application.Event.EventTestAuthSupport.FounderActor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds, UUID callerId) {}
    public record StandingSetup(UUID eventId, UUID areaId, UUID callerId) {}

    public static SeatingSetup publishedSeatingEvent(EventManagementService service,
                                                     IMemberRepository memberRepository,
                                                     int seatCount) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "concurrency", "a", Category.OTHER,
                Instant.now().plusSeconds(86400), "v", null, null), actor.memberId());
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        }
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "main", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null, specs), actor.memberId());
        service.publish(eventId, actor.memberId());

        EventDTO view = service.getEvent(eventId);
        List<UUID> seats = view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .map(EventDTO.SeatView::seatId)
                .toList();
        return new SeatingSetup(eventId, areaId, seats, actor.memberId());
    }

    public static StandingSetup publishedStandingEvent(EventManagementService service,
                                                       IMemberRepository memberRepository,
                                                       int capacity) {
        FounderActor actor = EventTestAuthSupport.newFounder(memberRepository);
        UUID eventId = service.createEvent(new CreateEventCommand(
                actor.companyId(), "concurrency standing", "a", Category.OTHER,
                Instant.now().plusSeconds(86400), "v", null, null), actor.memberId());
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "floor", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.STANDING, capacity, List.of()), actor.memberId());
        service.publish(eventId, actor.memberId());
        return new StandingSetup(eventId, areaId, actor.memberId());
    }
}
