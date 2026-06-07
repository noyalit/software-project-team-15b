package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        name = "app.external.mode",
        havingValue = "fake",
        matchIfMissing = true
)
public class FakeTicketSupplyAPI implements ITicketSupplyAPI {

    @Override
    public Map<UUID, String> issueStandingTickets(UUID customerId, UUID eventId, String areaName,
            Set<UUID> internalStandingTicketIds) {
        if (customerId == null || eventId == null || areaName == null || internalStandingTicketIds == null) {
            throw new IllegalArgumentException("All parameters are required");
        }
        // Simulate issuing tickets by generating random ticket IDs
        return internalStandingTicketIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> "TICKET-" + UUID.randomUUID().toString()
                ));
    }

    @Override
    public Map<UUID, String> issueSeatingTickets(UUID customerId, UUID eventId, String areaName,
            List<SeatTicketRequestDTO> seats) {
        if (customerId == null || eventId == null || areaName == null || seats == null) {
            throw new IllegalArgumentException("All parameters are required");
        }
        // Simulate issuing tickets by generating random ticket IDs
        return seats.stream()
                .collect(Collectors.toMap(
                        SeatTicketRequestDTO::internalSeatId,
                        seat -> "TICKET-" + UUID.randomUUID().toString()
                ));
    }

    @Override
    public void cancelTicket(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            throw new IllegalArgumentException("Ticket ID is required");
        }
        // Simulate ticket cancellation (no actual state management in this fake implementation)
    }

}