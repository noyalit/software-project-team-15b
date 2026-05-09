package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventTestFixtures {

    private EventTestFixtures() {}

    public static Money usd(String amount) {
        return Money.of(amount, "USD");
    }

    public static Event published(SeatingEventArea... seating) {
        return published(new StandingEventArea[0], seating);
    }

    public static Event published(StandingEventArea[] standing, SeatingEventArea[] seating) {
        Event event = draft();
        for (StandingEventArea a : standing) event.addArea(a);
        for (SeatingEventArea a : seating) event.addArea(a);
        event.publish();
        return event;
    }

    public static Event draft() {
        return new Event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Event",
                "Artist",
                Category.CONCERT,
                Instant.now().plusSeconds(86400),
                "Venue",
                List.of(),
                List.of()
        );
    }

    public static SeatingEventArea seatingArea(int seats, String priceStr) {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Main", usd(priceStr));
        for (int i = 1; i <= seats; i++) {
            area.addSeat(new Seat(UUID.randomUUID(), "A", String.valueOf(i)));
        }
        return area;
    }

    public static StandingEventArea standingArea(int capacity, String priceStr) {
        return new StandingEventArea(UUID.randomUUID(), "Floor", usd(priceStr), capacity);
    }
}
