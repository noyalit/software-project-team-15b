package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public String issueStandingTicket(UUID customerId, UUID eventId, String areaName,
            Set<UUID> internalStandingTicketIds) {
        if (customerId == null || eventId == null || areaName == null || internalStandingTicketIds == null) {
            throw new IllegalArgumentException("All parameters are required");
        }
        // Simulate issuing tickets by generating random ticket ID
        return "TICKET-" + UUID.randomUUID().toString();
    }

    @Override
    public String issueSeatingTicket(UUID customerId, UUID eventId, String areaName,
            List<SeatTicketRequestDTO> seats) {
        if (customerId == null || eventId == null || areaName == null || seats == null) {
            throw new IllegalArgumentException("All parameters are required");
        }
        // Simulate issuing tickets by generating random ticket ID
        return "TICKET-" + UUID.randomUUID().toString();
    }


    @Override
    public void cancelTicket(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            throw new IllegalArgumentException("Ticket ID is required");
        }
        // Simulate ticket cancellation (no actual state management in this fake implementation)
    }

}