package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.external.mode",
        havingValue = "fake",
        matchIfMissing = true
)
public class FakeTicketSupplyAPI implements ITicketSupplyAPI {

    @Override
    public Map<UUID, String> issueTickets(UUID customerId, UUID eventId, UUID areaId, Set<UUID> seatIds) {
        if (customerId == null || eventId == null || areaId == null || seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments for issuing tickets");
        }

        return Map.of(seatIds.iterator().next(), "fake-ticket-id");
    }

    @Override
    public void cancelTicket(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            throw new IllegalArgumentException("Invalid ticket id");
        }
        // do nothing, just simulate a successful cancellation

    }
}