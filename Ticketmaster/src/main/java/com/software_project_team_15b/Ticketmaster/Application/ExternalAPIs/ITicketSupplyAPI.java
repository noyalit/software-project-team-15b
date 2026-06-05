package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * External API for issuing and cancelling tickets.
 *
 * This interface represents the external ticket system used during checkout.
 */
public interface ITicketSupplyAPI {

    /**
     * Issues tickets for the given seats in an active order.
     *
     * @param customerId the id of the customer/user buying the tickets.
     * @param eventId the id of the event.
     * @param areaId the id of the area/zone in the event.
     * @param seatIds the ids of the seats to issue tickets for.
     * @return a map from seat id to external ticket id.
     *         The external ticket id must be stored because it is required
     *         for cancelling the ticket later.
     * @throws IllegalArgumentException if one of the arguments is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException
     *         if the external ticket system fails to issue the tickets.
     */
    Map<UUID, String> issueTickets(
            UUID customerId,
            UUID eventId,
            UUID areaId,
            Set<UUID> seatIds
    );

    /**
     * Cancels a previously issued ticket.
     *
     * The external ticket system cancels by ticket id, not by event/area/seat.
     *
     * @param ticketId the external ticket id returned by issueTickets.
     * @throws IllegalArgumentException if ticketId is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException
     *         if the external ticket system fails to cancel the ticket.
     */
    void cancelTicket(String ticketId);
}