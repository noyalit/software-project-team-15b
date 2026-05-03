package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.UUID;

public interface ITicketSupplyAPI {
    /**
     * An external API call to generate a ticket.
     * @param eventId ID of the event.
     * @param areaId ID of the area in the event.
     * @param seatId ID of the seat in the area.
     * @return Response object
     */
    public Response<Boolean> issueTicket(UUID eventId, UUID areaId, UUID seatId);

    /**
     * An external API call to cancel an existing ticket.
     * @param eventId ID of the event.
     * @param areaId ID of the area in the event.
     * @param seatId ID of the seat in the area.
     * @return Response object
     */
    public Response<Boolean> cancelTicket(UUID eventId, UUID areaId, UUID seatId);
}
