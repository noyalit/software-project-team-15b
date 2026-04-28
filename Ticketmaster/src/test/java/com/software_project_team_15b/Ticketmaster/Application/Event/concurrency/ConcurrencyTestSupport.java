package com.software_project_team_15b.Ticketmaster.Application.Event.concurrency;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds) {}
    public record StandingSetup(UUID eventId, UUID areaId) {}

    public static SeatingSetup publishedSeatingEvent(EventManagementService service, int seatCount) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "concurrency", "a", Category.OTHER,
                Instant.now().plusSeconds(86400), "v", null, null), caller);
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        }
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "main", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null, specs), caller);
        service.publish(eventId, caller);

        EventView view = service.getEvent(eventId);
        List<UUID> seats = view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow()
                .seats().stream()
                .map(EventView.SeatView::seatId)
                .toList();
        return new SeatingSetup(eventId, areaId, seats);
    }

    public static StandingSetup publishedStandingEvent(EventManagementService service, int capacity) {
        UUID caller = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                companyId, "concurrency standing", "a", Category.OTHER,
                Instant.now().plusSeconds(86400), "v", null, null), caller);
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "floor", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.STANDING, capacity, List.of()), caller);
        service.publish(eventId, caller);
        return new StandingSetup(eventId, areaId);
    }
}
