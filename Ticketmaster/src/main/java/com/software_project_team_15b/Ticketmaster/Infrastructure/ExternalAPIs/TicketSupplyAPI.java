package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class TicketSupplyAPI implements ITicketSupplyAPI {

    private static final String BASE_URL = "https://damp-lynna-wsep-1984852e.koyeb.app/";

    private final ExternalApiHttpClient httpClient;

    public TicketSupplyAPI() {
        this.httpClient = new ExternalApiHttpClient(BASE_URL);
    }

    // Useful for tests
    public TicketSupplyAPI(ExternalApiHttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient cannot be null");
        }

        this.httpClient = httpClient;
    }

    @Override
    public Map<UUID, String> issueStandingTickets(
            UUID customerId,
            UUID eventId,
            UUID areaId,
            Set<UUID> internalStandingTicketIds
    ) {
        validateStandingRequest(customerId, eventId, areaId, internalStandingTicketIds);

        Map<String, String> body = new HashMap<>();
        body.put("action_type", "issue_ticket");
        body.put("customer_id", customerId.toString());
        body.put("event_id", eventId.toString());
        body.put("zone", areaId.toString());
        body.put("quantity", String.valueOf(internalStandingTicketIds.size()));

        String response = httpClient.postForm(body).trim();
        String externalTicketId = parseIssuedTicketId(response, "standing tickets");

        return mapStandingIdsToExternalTicketId(internalStandingTicketIds, externalTicketId);
    }

    @Override
    public Map<UUID, String> issueSeatingTickets(
            UUID customerId,
            UUID eventId,
            UUID areaId,
            List<SeatTicketRequestDTO> seats
    ) {
        validateSeatingRequest(customerId, eventId, areaId, seats);

        Map<String, String> body = new HashMap<>();
        body.put("action_type", "issue_ticket");
        body.put("customer_id", customerId.toString());
        body.put("event_id", eventId.toString());
        body.put("zone", areaId.toString());
        body.put("is_seating", "true");
        body.put("seats", buildSeatsJson(seats));

        String response = httpClient.postForm(body).trim();
        String externalTicketId = parseIssuedTicketId(response, "seating tickets");

        return mapSeatingIdsToExternalTicketId(seats, externalTicketId);
    }

    @Override
    public void cancelTicket(String ticketId) {
        validateTicketId(ticketId);

        Map<String, String> body = Map.of(
                "action_type", "cancel_ticket",
                "ticket_id", ticketId
        );

        String response = httpClient.postForm(body).trim();

        int result;
        try {
            result = Integer.parseInt(response);
        } catch (NumberFormatException e) {
            throw new FailedToIssueTicketsException(
                    "Ticket API returned invalid cancel response: " + response
            );
        }

        if (result != 1) {
            throw new FailedToIssueTicketsException(
                    "External ticket system failed to cancel ticket " + ticketId
            );
        }
    }

    private String buildSeatsJson(List<SeatTicketRequestDTO> seats) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");

        for (int i = 0; i < seats.size(); i++) {
            SeatTicketRequestDTO seat = seats.get(i);

            builder.append("{")
                    .append("\"row\":")
                    .append(seat.row())
                    .append(",")
                    .append("\"seat\":")
                    .append(seat.seat())
                    .append("}");

            if (i < seats.size() - 1) {
                builder.append(",");
            }
        }

        builder.append("]");
        return builder.toString();
    }

    private Map<UUID, String> mapStandingIdsToExternalTicketId(
            Set<UUID> internalStandingTicketIds,
            String externalTicketId
    ) {
        Map<UUID, String> result = new LinkedHashMap<>();

        for (UUID internalTicketId : internalStandingTicketIds) {
            result.put(internalTicketId, externalTicketId);
        }

        return result;
    }

    private Map<UUID, String> mapSeatingIdsToExternalTicketId(
            List<SeatTicketRequestDTO> seats,
            String externalTicketId
    ) {
        Map<UUID, String> result = new LinkedHashMap<>();

        for (SeatTicketRequestDTO seat : seats) {
            result.put(seat.internalSeatId(), externalTicketId);
        }

        return result;
    }

    private String parseIssuedTicketId(String response, String context) {
        if (response == null || response.isBlank()) {
            throw new FailedToIssueTicketsException(
                    "External ticket system returned empty ticket id for " + context
            );
        }

        if (response.equals("-1")) {
            throw new FailedToIssueTicketsException(
                    "External ticket system failed to issue " + context
            );
        }

        return response;
    }

    private void validateStandingRequest(
            UUID customerId,
            UUID eventId,
            UUID areaId,
            Set<UUID> internalStandingTicketIds
    ) {
        validateCommonIssueArgs(customerId, eventId, areaId);

        if (internalStandingTicketIds == null || internalStandingTicketIds.isEmpty()) {
            throw new IllegalArgumentException("internalStandingTicketIds cannot be null or empty");
        }

        if (internalStandingTicketIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("internalStandingTicketIds cannot contain null values");
        }
    }

    private void validateSeatingRequest(
            UUID customerId,
            UUID eventId,
            UUID areaId,
            List<SeatTicketRequestDTO> seats
    ) {
        validateCommonIssueArgs(customerId, eventId, areaId);

        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("seats cannot be null or empty");
        }

        for (SeatTicketRequestDTO seat : seats) {
            if (seat == null) {
                throw new IllegalArgumentException("seats cannot contain null values");
            }

            if (seat.internalSeatId() == null) {
                throw new IllegalArgumentException("internalSeatId cannot be null");
            }

            if (seat.row() <= 0) {
                throw new IllegalArgumentException("row must be positive");
            }

            if (seat.seat() <= 0) {
                throw new IllegalArgumentException("seat must be positive");
            }
        }
    }

    private void validateCommonIssueArgs(
            UUID customerId,
            UUID eventId,
            UUID areaId
    ) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId cannot be null");
        }

        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }

        if (areaId == null) {
            throw new IllegalArgumentException("areaId cannot be null");
        }
    }

    private void validateTicketId(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("ticketId cannot be null or blank");
        }
    }
}
