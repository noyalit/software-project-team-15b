package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.Set;
import java.util.UUID;

public interface ITicketSupplyAPI {
    /**
     * An external API call to generate tickets.
     * @param eventId ID of the event.
     * @param areaId ID of the area in the event.
     * @param seatIds IDs of the seats in the area.
     * @return Response object.
     */
    public Response<Boolean> issueTickets(UUID eventId, UUID areaId, Set<UUID> seatIds);

    /**
     * An external API call to cancel existing tickets.
     * @param eventId ID of the event.
     * @param areaId ID of the area in the event.
     * @param seatIds IDs of the seats in the area.
     * @return Response object.
     */
    public Response<Boolean> cancelTickets(UUID eventId, UUID areaId, Set<UUID> seatIds);
}
