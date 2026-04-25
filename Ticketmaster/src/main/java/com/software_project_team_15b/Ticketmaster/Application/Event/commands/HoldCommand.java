package com.software_project_team_15b.Ticketmaster.Application.Event.commands;

import java.util.List;
import java.util.UUID;

/**
 * Hold command for the Event application service.
 *
 * No TTL / expiry is carried here: the Event aggregate itself does not manage
 * timers. Scheduling release-on-expiry is the responsibility of an external
 * reservation-timer component which will call {@code release(...)} on the
 * application service when the hold's TTL elapses.
 */
public record HoldCommand(
        UUID areaId,
        List<UUID> seatIds,
        Integer standingQuantity,
        UUID holdToken
) {
    public boolean isStanding() { return standingQuantity != null; }
}
