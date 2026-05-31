package com.software_project_team_15b.Ticketmaster.black.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.Event.EventController;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventControllerBlackTest {

    @Mock
    private IEventManagementService eventService;

    @InjectMocks
    private EventController controller;

    private static final String TOKEN = "Bearer token";

    private CreateEventCommand createCmd() {
        return new CreateEventCommand(UUID.randomUUID(), "Concert", "Artist",
                Category.CONCERT, Instant.parse("2030-01-01T20:00:00Z"),
                "Tel Aviv", List.of(), List.of());
    }

    private UpdateEventCommand updateCmd() {
        return new UpdateEventCommand("Updated", null, null, null, null);
    }

    private PriceQuery priceQuery() {
        return new PriceQuery(UUID.randomUUID(), 2, UUID.randomUUID(), null, null);
    }

    private PurchaseRequest purchaseRequest(UUID eventId) {
        return new PurchaseRequest(eventId, UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, null);
    }

    // ─── createEvent ──────────────────────────────────────────────────────────

    @Test
    void GivenValidCommand_WhenCreateEvent_ThenReturn201WithEventId() {
        UUID eventId = UUID.randomUUID();
        when(eventService.createEvent(any(), anyString())).thenReturn(eventId);

        ResponseEntity<ApiResponse<UUID>> response = controller.createEvent(TOKEN, createCmd());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(eventId, response.getBody().getData());
    }

    @Test
    void GivenInvalidToken_WhenCreateEvent_ThenReturn401() {
        when(eventService.createEvent(any(), anyString()))
                .thenThrow(new InvalidTokenException("Invalid token"));

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.createEvent("bad", createCmd()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenCreateEvent_ThenReturn403() {
        when(eventService.createEvent(any(), anyString()))
                .thenThrow(new PolicyViolationException("Not allowed"));

        assertEquals(HttpStatus.FORBIDDEN,
                controller.createEvent(TOKEN, createCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidEventState_WhenCreateEvent_ThenReturn409() {
        when(eventService.createEvent(any(), anyString()))
                .thenThrow(new InvalidEventStateException("Conflict"));

        assertEquals(HttpStatus.CONFLICT,
                controller.createEvent(TOKEN, createCmd()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenCreateEvent_ThenReturn400() {
        when(eventService.createEvent(any(), anyString()))
                .thenThrow(new IllegalArgumentException("Bad input"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.createEvent(TOKEN, createCmd()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenCreateEvent_ThenReturn500() {
        when(eventService.createEvent(any(), anyString()))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.createEvent(TOKEN, createCmd()).getStatusCode());
    }

    // ─── getEvent ─────────────────────────────────────────────────────────────

    @Test
    void GivenExistingEvent_WhenGetEvent_ThenReturn200WithEvent() {
        UUID eventId = UUID.randomUUID();
        EventDTO dto = new EventDTO(eventId, UUID.randomUUID(), "Concert", "Artist",
                Category.CONCERT, Instant.parse("2030-01-01T20:00:00Z"),
                "Tel Aviv", EventStatus.PUBLISHED, List.of());
        when(eventService.getEvent(eq(eventId))).thenReturn(dto);

        ResponseEntity<ApiResponse<EventDTO>> response = controller.getEvent(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Concert", response.getBody().getData().name());
    }

    @Test
    void GivenNonExistentEvent_WhenGetEvent_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEvent(eq(eventId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getEvent(eventId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetEvent_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEvent(eq(eventId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getEvent(eventId).getStatusCode());
    }

    // ─── updateEvent ──────────────────────────────────────────────────────────

    @Test
    void GivenValidCommand_WhenUpdateEvent_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).updateEvent(eq(eventId), any(), anyString());

        assertEquals(HttpStatus.OK,
                controller.updateEvent(eventId, TOKEN, updateCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenUpdateEvent_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .updateEvent(any(), any(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.updateEvent(eventId, "bad", updateCmd()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenUpdateEvent_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .updateEvent(any(), any(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.updateEvent(eventId, TOKEN, updateCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidState_WhenUpdateEvent_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .updateEvent(any(), any(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.updateEvent(eventId, TOKEN, updateCmd()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenUpdateEvent_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Bad")).when(eventService)
                .updateEvent(any(), any(), anyString());

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updateEvent(eventId, TOKEN, updateCmd()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenUpdateEvent_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .updateEvent(any(), any(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.updateEvent(eventId, TOKEN, updateCmd()).getStatusCode());
    }

    // ─── publish ──────────────────────────────────────────────────────────────

    @Test
    void GivenDraftEvent_WhenPublish_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).publish(eq(eventId), anyString());

        assertEquals(HttpStatus.OK,
                controller.publish(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenPublish_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .publish(any(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.publish(eventId, "bad").getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenPublish_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .publish(any(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.publish(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidState_WhenPublish_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .publish(any(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.publish(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenPublish_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .publish(any(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.publish(eventId, TOKEN).getStatusCode());
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    void GivenPublishedEvent_WhenCancel_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).cancel(eq(eventId), anyString());

        assertEquals(HttpStatus.OK,
                controller.cancel(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenCancel_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .cancel(any(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.cancel(eventId, "bad").getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenCancel_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .cancel(any(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.cancel(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidState_WhenCancel_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Not found")).when(eventService)
                .cancel(any(), anyString());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.cancel(eventId, TOKEN).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenCancel_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .cancel(any(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.cancel(eventId, TOKEN).getStatusCode());
    }

    // ─── getAvailability ──────────────────────────────────────────────────────

    @Test
    void GivenPublishedEvent_WhenGetAvailability_ThenReturn200WithStatus() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEventAvailability(eq(eventId)))
                .thenReturn(new EventAvailabilityDTO(EventAvailability.AVAILABLE));

        ResponseEntity<ApiResponse<EventAvailabilityDTO>> response =
                controller.getAvailability(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EventAvailability.AVAILABLE, response.getBody().getData().status());
    }

    @Test
    void GivenNonExistentEvent_WhenGetAvailability_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEventAvailability(eq(eventId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getAvailability(eventId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetAvailability_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEventAvailability(eq(eventId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getAvailability(eventId).getStatusCode());
    }

    // ─── search ───────────────────────────────────────────────────────────────

    @Test
    void GivenValidCriteria_WhenSearch_ThenReturn200WithResults() {
        UUID eventId = UUID.randomUUID();
        EventDTO dto = new EventDTO(eventId, UUID.randomUUID(), "Concert", "Artist",
                Category.CONCERT, Instant.parse("2030-01-01T20:00:00Z"),
                "Tel Aviv", EventStatus.PUBLISHED, List.of());
        when(eventService.search(any())).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<EventDTO>>> response =
                controller.search(SearchCriteria.empty());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Concert", response.getBody().getData().get(0).name());
    }

    @Test
    void GivenNullBody_WhenSearch_ThenReturn200WithAll() {
        when(eventService.search(any())).thenReturn(List.of());

        assertEquals(HttpStatus.OK, controller.search(null).getStatusCode());
    }

    @Test
    void GivenInvalidArgument_WhenSearch_ThenReturn400() {
        when(eventService.search(any()))
                .thenThrow(new IllegalArgumentException("Bad criteria"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.search(SearchCriteria.empty()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenSearch_ThenReturn500() {
        when(eventService.search(any()))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.search(SearchCriteria.empty()).getStatusCode());
    }

    // ─── getPriceQuote ────────────────────────────────────────────────────────

    @Test
    void GivenValidQuery_WhenGetPriceQuote_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        MoneyDTO money = new MoneyDTO(new BigDecimal("100.00"), "ILS");
        PriceBreakdownDTO priceDto = new PriceBreakdownDTO(money, money,
                new MoneyDTO(BigDecimal.TEN, "ILS"), money);
        when(eventService.getPrice(eq(eventId), any())).thenReturn(priceDto);

        assertEquals(HttpStatus.OK,
                controller.getPriceQuote(eventId, priceQuery()).getStatusCode());
    }

    @Test
    void GivenNonExistentEvent_WhenGetPriceQuote_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPrice(any(), any()))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getPriceQuote(eventId, priceQuery()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenGetPriceQuote_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPrice(any(), any()))
                .thenThrow(new IllegalArgumentException("Bad query"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.getPriceQuote(eventId, priceQuery()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetPriceQuote_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPrice(any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getPriceQuote(eventId, priceQuery()).getStatusCode());
    }

    // ─── validateEligibility ─────────────────────────────────────────────────

    @Test
    void GivenEligibleBuyer_WhenValidateEligibility_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).validatePurchaseEligibility(eq(eventId), any());

        assertEquals(HttpStatus.OK,
                controller.validateEligibility(eventId, purchaseRequest(eventId)).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenValidateEligibility_ThenReturn422() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Age restricted")).when(eventService)
                .validatePurchaseEligibility(any(), any());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT,
                controller.validateEligibility(eventId, purchaseRequest(eventId)).getStatusCode());
    }

    @Test
    void GivenNonExistentEvent_WhenValidateEligibility_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Not found")).when(eventService)
                .validatePurchaseEligibility(any(), any());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.validateEligibility(eventId, purchaseRequest(eventId)).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenValidateEligibility_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Bad request")).when(eventService)
                .validatePurchaseEligibility(any(), any());

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.validateEligibility(eventId, purchaseRequest(eventId)).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenValidateEligibility_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .validatePurchaseEligibility(any(), any());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.validateEligibility(eventId, purchaseRequest(eventId)).getStatusCode());
    }
}
