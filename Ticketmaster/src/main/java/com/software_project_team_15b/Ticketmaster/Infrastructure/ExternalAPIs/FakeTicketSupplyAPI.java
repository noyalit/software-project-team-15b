package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    public Response<Boolean> issueTickets(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        if (eventId == null || areaId == null || seatIds == null || seatIds.isEmpty()) {
            return new Response<>("eventId, areaId, and seatIds are required");
        }

        return new Response<>(true);
    }

    @Override
    public Response<Boolean> cancelTickets(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        if (eventId == null || areaId == null || seatIds == null || seatIds.isEmpty()) {
            return new Response<>("eventId, areaId, and seatIds are required");
        }

        return new Response<>(true);
    }
}