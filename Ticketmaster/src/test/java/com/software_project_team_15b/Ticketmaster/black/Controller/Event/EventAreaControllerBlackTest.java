package com.software_project_team_15b.Ticketmaster.black.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.Event.EventAreaController;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.AreaAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAreaControllerBlackTest {

    @Mock
    private IEventManagementService eventService;

    @InjectMocks
    private EventAreaController controller;

    private static final String TOKEN = "Bearer token";

    private AddAreaCommand addAreaCmd() {
        return new AddAreaCommand("VIP", new Money(new BigDecimal("100.00"), "ILS"),
                AddAreaCommand.AreaType.STANDING, 200, List.of());
    }

    private UpdateAreaCommand updateAreaCmd() {
        return new UpdateAreaCommand("General", null, null);
    }

    // ─── addArea ──────────────────────────────────────────────────────────────

    @Test
    void GivenValidCommand_WhenAddArea_ThenReturn201WithAreaId() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.addArea(eq(eventId), any(), anyString())).thenReturn(areaId);

        ResponseEntity<ApiResponse<UUID>> response = controller.addArea(eventId, TOKEN, addAreaCmd());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(areaId, response.getBody().getData());
    }

    @Test
    void GivenInvalidToken_WhenAddArea_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        when(eventService.addArea(any(), any(), anyString()))
                .thenThrow(new InvalidTokenException("Bad token"));

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.addArea(eventId, "bad", addAreaCmd()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenAddArea_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        when(eventService.addArea(any(), any(), anyString()))
                .thenThrow(new PolicyViolationException("Forbidden"));

        assertEquals(HttpStatus.FORBIDDEN,
                controller.addArea(eventId, TOKEN, addAreaCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidEventState_WhenAddArea_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        when(eventService.addArea(any(), any(), anyString()))
                .thenThrow(new InvalidEventStateException("Conflict"));

        assertEquals(HttpStatus.CONFLICT,
                controller.addArea(eventId, TOKEN, addAreaCmd()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenAddArea_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        when(eventService.addArea(any(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("Bad input"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.addArea(eventId, TOKEN, addAreaCmd()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenAddArea_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.addArea(any(), any(), anyString()))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.addArea(eventId, TOKEN, addAreaCmd()).getStatusCode());
    }

    // ─── updateArea ───────────────────────────────────────────────────────────

    @Test
    void GivenValidCommand_WhenUpdateArea_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doNothing().when(eventService).updateArea(eq(eventId), eq(areaId), any(), anyString());

        assertEquals(HttpStatus.OK,
                controller.updateArea(eventId, areaId, TOKEN, updateAreaCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenUpdateArea_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .updateArea(any(), any(), any(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.updateArea(eventId, areaId, "bad", updateAreaCmd()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenUpdateArea_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .updateArea(any(), any(), any(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.updateArea(eventId, areaId, TOKEN, updateAreaCmd()).getStatusCode());
    }

    @Test
    void GivenInvalidState_WhenUpdateArea_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .updateArea(any(), any(), any(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.updateArea(eventId, areaId, TOKEN, updateAreaCmd()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenUpdateArea_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Bad")).when(eventService)
                .updateArea(any(), any(), any(), anyString());

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updateArea(eventId, areaId, TOKEN, updateAreaCmd()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenUpdateArea_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .updateArea(any(), any(), any(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.updateArea(eventId, areaId, TOKEN, updateAreaCmd()).getStatusCode());
    }

    // ─── removeArea ───────────────────────────────────────────────────────────

    @Test
    void GivenValidRequest_WhenRemoveArea_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doNothing().when(eventService).removeArea(eq(eventId), eq(areaId), anyString());

        assertEquals(HttpStatus.OK,
                controller.removeArea(eventId, areaId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenRemoveArea_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .removeArea(any(), any(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.removeArea(eventId, areaId, "bad").getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenRemoveArea_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .removeArea(any(), any(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.removeArea(eventId, areaId, TOKEN).getStatusCode());
    }

    @Test
    void GivenInvalidEventState_WhenRemoveArea_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .removeArea(any(), any(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.removeArea(eventId, areaId, TOKEN).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenRemoveArea_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .removeArea(any(), any(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.removeArea(eventId, areaId, TOKEN).getStatusCode());
    }

    // ─── getAreaAvailability ──────────────────────────────────────────────────

    @Test
    void GivenAvailableArea_WhenGetAreaAvailability_ThenReturn200True() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getAreaAvailability(eq(eventId), eq(areaId))).thenReturn(true);

        ResponseEntity<ApiResponse<AreaAvailabilityDTO>> response =
                controller.getAreaAvailability(eventId, areaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().available());
    }

    @Test
    void GivenSoldOutArea_WhenGetAreaAvailability_ThenReturn200False() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getAreaAvailability(eq(eventId), eq(areaId))).thenReturn(false);

        ResponseEntity<ApiResponse<AreaAvailabilityDTO>> response =
                controller.getAreaAvailability(eventId, areaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getData().available());
    }

    @Test
    void GivenNonExistentArea_WhenGetAreaAvailability_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getAreaAvailability(eq(eventId), eq(areaId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getAreaAvailability(eventId, areaId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetAreaAvailability_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getAreaAvailability(eq(eventId), eq(areaId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getAreaAvailability(eventId, areaId).getStatusCode());
    }

    // ─── areaSeats ────────────────────────────────────────────────────────────

    @Test
    void GivenValidArea_WhenAreaSeats_ThenReturn200WithList() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.areaSeats(eq(eventId), eq(areaId))).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<EventDTO.SeatView>>> response =
                controller.areaSeats(eventId, areaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void GivenNonExistentArea_WhenAreaSeats_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.areaSeats(eq(eventId), eq(areaId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.areaSeats(eventId, areaId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenAreaSeats_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.areaSeats(eq(eventId), eq(areaId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.areaSeats(eventId, areaId).getStatusCode());
    }

    // ─── seatsAvailability ────────────────────────────────────────────────────

    @Test
    void GivenValidSeatIds_WhenSeatsAvailability_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        SeatsAvailabilityDTO dto = new SeatsAvailabilityDTO(Set.of(seatId), Set.of());
        when(eventService.getSeatsAvailability(eq(eventId), eq(areaId), any())).thenReturn(dto);

        ResponseEntity<ApiResponse<SeatsAvailabilityDTO>> response =
                controller.seatsAvailability(eventId, areaId, Set.of(seatId));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody().getData());
    }

    @Test
    void GivenNonExistentArea_WhenSeatsAvailability_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getSeatsAvailability(any(), any(), any()))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.seatsAvailability(eventId, areaId, Set.of(UUID.randomUUID())).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenSeatsAvailability_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getSeatsAvailability(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Bad seat ids"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.seatsAvailability(eventId, areaId, Set.of(UUID.randomUUID())).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenSeatsAvailability_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(eventService.getSeatsAvailability(any(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.seatsAvailability(eventId, areaId, Set.of(UUID.randomUUID())).getStatusCode());
    }
}
