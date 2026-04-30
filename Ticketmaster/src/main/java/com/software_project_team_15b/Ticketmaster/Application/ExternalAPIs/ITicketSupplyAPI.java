package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.UUID;

public interface ITicketSupplyAPI {
    /**
     * An external API call to generate a ticket.
     * @param eventId ID of the event.
     * @param areaId ID of the area in the event.
     * @param seatId ID of the seat in the area.
     * @param paymentId payment transaction ID.
     * @return Response containing the ticket ID if successful, or an error message if failed
     */
    public Response<UUID> issueTicket(UUID eventId, UUID areaId, UUID seatId, UUID paymentId);

    /**
     * An external API call to cancel an existing ticket
     * @param ticketId ID of the ticket to cancel.
     * @return Response containing the ticket ID if successful, or an error message if failed
     */
    public Response<UUID> cancelTicket(UUID ticketId);
}
