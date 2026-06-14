package com.software_project_team_15b.Ticketmaster.white.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.DTO.SeatTicketRequestDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToCancelTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs.ExternalApiHttpClient;
import com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs.TicketSupplyAPI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSupplyAPITest {

    @Mock
    private ExternalApiHttpClient httpClient;

    @Test
    void issueStandingTicketShouldSendQuantityAndReturnTicketId() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId1 = UUID.randomUUID();
        UUID seatId2 = UUID.randomUUID();

        when(httpClient.postForm(anyMap()))
                .thenReturn("TIX-STANDING-123");

        String result = api.issueStandingTicket(
                customerId,
                eventId,
                "Golden Ring",
                Set.of(seatId1, seatId2)
        );

        assertEquals("TIX-STANDING-123", result);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(captor.capture());

        Map<String, String> body = captor.getValue();

        assertEquals("issue_ticket", body.get("action_type"));
        assertEquals(customerId.toString(), body.get("customer_id"));
        assertEquals(eventId.toString(), body.get("event_id"));
        assertEquals("Golden Ring", body.get("zone"));
        assertEquals("2", body.get("quantity"));
        assertFalse(body.containsKey("is_seating"));
        assertFalse(body.containsKey("seats"));
    }

    @Test
    void issueStandingTicketShouldThrowWhenExternalApiReturnsMinusOne() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("-1");

        assertThrows(FailedToIssueTicketsException.class, () ->
                api.issueStandingTicket(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Golden Ring",
                        Set.of(UUID.randomUUID())
                )
        );
    }

    @Test
    void issueStandingTicketShouldRejectInvalidArguments() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                api.issueStandingTicket(null, eventId, "Golden Ring", Set.of(UUID.randomUUID()))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueStandingTicket(customerId, null, "Golden Ring", Set.of(UUID.randomUUID()))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueStandingTicket(customerId, eventId, " ", Set.of(UUID.randomUUID()))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueStandingTicket(customerId, eventId, "Golden Ring", null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueStandingTicket(customerId, eventId, "Golden Ring", Set.of())
        );

        verifyNoInteractions(httpClient);
    }

    @Test
    void issueSeatingTicketShouldSendSeatsJsonAndReturnTicketId() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID internalSeatId1 = UUID.randomUUID();
        UUID internalSeatId2 = UUID.randomUUID();

        List<SeatTicketRequestDTO> seats = List.of(
                new SeatTicketRequestDTO(internalSeatId1, 4, 12),
                new SeatTicketRequestDTO(internalSeatId2, 4, 13)
        );

        when(httpClient.postForm(anyMap()))
                .thenReturn("TIX-SEATING-123");

        String result = api.issueSeatingTicket(
                customerId,
                eventId,
                "VIP Balcony",
                seats
        );

        assertEquals("TIX-SEATING-123", result);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(captor.capture());

        Map<String, String> body = captor.getValue();

        assertEquals("issue_ticket", body.get("action_type"));
        assertEquals(customerId.toString(), body.get("customer_id"));
        assertEquals(eventId.toString(), body.get("event_id"));
        assertEquals("VIP Balcony", body.get("zone"));
        assertEquals("true", body.get("is_seating"));
        assertEquals("[{\"row\":4,\"seat\":12},{\"row\":4,\"seat\":13}]", body.get("seats"));
        assertFalse(body.containsKey("quantity"));
    }

    @Test
    void issueSeatingTicketShouldThrowWhenExternalApiReturnsMinusOne() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("-1");

        assertThrows(FailedToIssueTicketsException.class, () ->
                api.issueSeatingTicket(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 4, 12))
                )
        );
    }

    @Test
    void issueSeatingTicketShouldRejectInvalidArguments() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(null, eventId, "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 4, 12)))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, null, "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 4, 12)))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, " ",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 4, 12)))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, "VIP Balcony", null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, "VIP Balcony", List.of())
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(null, 4, 12)))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 0, 12)))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.issueSeatingTicket(customerId, eventId, "VIP Balcony",
                        List.of(new SeatTicketRequestDTO(UUID.randomUUID(), 4, 0)))
        );

        verifyNoInteractions(httpClient);
    }

    @Test
    void cancelTicketShouldSendCancelRequest() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("1");

        api.cancelTicket("TIX-123");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(captor.capture());

        Map<String, String> body = captor.getValue();

        assertEquals("cancel_ticket", body.get("action_type"));
        assertEquals("TIX-123", body.get("ticket_id"));
    }

    @Test
    void cancelTicketShouldThrowWhenExternalCancellationFails() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("-1");

        assertThrows(FailedToCancelTicketsException.class, () ->
                api.cancelTicket("TIX-123")
        );
    }

    @Test
    void cancelTicketShouldThrowWhenExternalCancellationReturnsInvalidResponse() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("not-a-number");

        assertThrows(FailedToCancelTicketsException.class, () ->
                api.cancelTicket("TIX-123")
        );
    }

    @Test
    void cancelTicketShouldRejectBlankTicketId() {
        TicketSupplyAPI api = new TicketSupplyAPI(httpClient);

        assertThrows(IllegalArgumentException.class, () ->
                api.cancelTicket(null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.cancelTicket(" ")
        );

        verifyNoInteractions(httpClient);
    }
}