package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * External API for issuing and cancelling tickets.
 *
 * This interface represents the external ticket system used during checkout.
 *
 * The application service should use this interface and should not know
 * the concrete HTTP implementation details.
 *
 * The external ticket system supports two different ticket issuing flows:
 *
 * 1. Standing / general admission tickets:
 *    the external system receives a quantity of tickets to issue.
 *
 * 2. Assigned seating tickets:
 *    the external system receives a list of seats, where each seat contains
 *    row and seat identifiers.
 */
public interface ITicketSupplyAPI {

    /**
     * Issues standing / general admission tickets.
     *
     * This method should be used for areas/zones that do not have assigned seating.
     *
     * The external ticket system expects the request to include:
     * customer id, event id, zone, and quantity.
     *
     * In our system, even standing tickets may have internal ids.
     * These ids are not sent to the external ticket system as seats.
     * They are used only by our system in order to match each internally reserved
     * ticket/seat id to the external ticket id returned by the ticket system.
     *
     * @param customerId the id of the customer/user buying the tickets.
     * @param eventId the id of the event.
     * @param areaName the name of the area/zone in the event.
     *                 This value is sent to the external system as the "zone" parameter.
     * @param internalStandingTicketIds the internal ids of the standing tickets/seats
     *                                  reserved in our system.
     *                                  The number of ids determines the quantity
     *                                  sent to the external ticket system.
     * @return a map from each internal standing ticket/seat id to the external ticket id.
     *         The external ticket id must be stored because it is required
     *         for cancelling the ticket later.
     * @throws IllegalArgumentException if one of the arguments is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException
     *         if the external ticket system fails to issue the tickets.
     */
    Map<UUID, String> issueStandingTickets(
            UUID customerId,
            UUID eventId,
            String areaName,
            Set<UUID> internalStandingTicketIds
    );

    /**
     * Issues assigned seating tickets.
     *
     * This method should be used for areas/zones that have assigned seating.
     *
     * The external ticket system expects the request to include:
     * customer id, event id, zone, is_seating=true, and seats.
     *
     * Each seat sent to the external system should contain the row and seat
     * identifiers required by the external API.
     *
     * The internalSeatId inside each SeatTicketRequestDTO is not sent as the
     * external row/seat value. It is used by our system in order to map the
     * issued external ticket id back to the internal seat id.
     *
     * @param customerId the id of the customer/user buying the tickets.
     * @param eventId the id of the event.
     * @param areaName the name of the area/zone in the event.
     *                 This value is sent to the external system as the "zone" parameter.
     * @param seats the assigned seats to issue tickets for.
     *              Each item should include the internal seat id and the row/seat
     *              identifiers required by the external ticket system.
     * @return a map from each internal seat id to the external ticket id.
     *         The external ticket id must be stored because it is required
     *         for cancelling the ticket later.
     * @throws IllegalArgumentException if one of the arguments is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException
     *         if the external ticket system fails to issue the tickets.
     */
    Map<UUID, String> issueSeatingTickets(
            UUID customerId,
            UUID eventId,
            String areaName,
            List<SeatTicketRequestDTO> seats
    );

    /**
     * Cancels a previously issued ticket.
     *
     * The external ticket system cancels by ticket id, not by user id,
     * event id, area id, row, seat, or internal seat id.
     *
     * @param ticketId the external ticket id returned by one of the issue methods.
     * @throws IllegalArgumentException if ticketId is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException
     *         if the external ticket system fails to cancel the ticket.
     */
    void cancelTicket(String ticketId);
}