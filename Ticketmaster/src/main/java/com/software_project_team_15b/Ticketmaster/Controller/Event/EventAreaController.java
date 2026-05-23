package com.software_project_team_15b.Ticketmaster.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.AreaAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/events/{eventId}/areas", produces = "application/json")
@Tag(name = "Event Areas", description = "Manage seating / standing areas and seat availability")
public class EventAreaController {

    private final IEventManagementService eventService;

    public EventAreaController(IEventManagementService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "Add a seating or standing area to a DRAFT event")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<UUID>> addArea(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody AddAreaCommand cmd) {
        try {
            UUID areaId = eventService.addArea(eventId, cmd, token);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(areaId, null));
        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (PolicyViolationException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Patch an area's name, price, or standing capacity")
    @PatchMapping(path = "/{areaId}", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> updateArea(
            @PathVariable UUID eventId,
            @PathVariable UUID areaId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody UpdateAreaCommand cmd) {
        try {
            eventService.updateArea(eventId, areaId, cmd, token);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (PolicyViolationException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Remove an area (DRAFT only)")
    @DeleteMapping("/{areaId}")
    public ResponseEntity<ApiResponse<Void>> removeArea(
            @PathVariable UUID eventId,
            @PathVariable UUID areaId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        try {
            eventService.removeArea(eventId, areaId, token);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (PolicyViolationException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Whether an area has any available capacity")
    @GetMapping("/{areaId}/availability")
    public ResponseEntity<ApiResponse<AreaAvailabilityDTO>> getAreaAvailability(
            @PathVariable UUID eventId,
            @PathVariable UUID areaId) {
        try {
            boolean available = eventService.getAreaAvailability(eventId, areaId);
            return ResponseEntity.ok(new ApiResponse<>(new AreaAvailabilityDTO(available), null));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "List every seat in an area with its status")
    @GetMapping("/{areaId}/seats")
    public ResponseEntity<ApiResponse<List<EventDTO.SeatView>>> areaSeats(
            @PathVariable UUID eventId,
            @PathVariable UUID areaId) {
        try {
            List<EventDTO.SeatView> result = eventService.areaSeats(eventId, areaId);
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Classify supplied seat ids as available / unavailable")
    @PostMapping(path = "/{areaId}/seats/availability", consumes = "application/json")
    public ResponseEntity<ApiResponse<SeatsAvailabilityDTO>> seatsAvailability(
            @PathVariable UUID eventId,
            @PathVariable UUID areaId,
            @RequestBody Set<UUID> seatIds) {
        try {
            SeatsAvailabilityDTO result = eventService.getSeatsAvailability(eventId, areaId, seatIds);
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }
}
