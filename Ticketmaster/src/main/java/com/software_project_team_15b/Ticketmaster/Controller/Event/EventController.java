package com.software_project_team_15b.Ticketmaster.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/events", produces = "application/json")
@Tag(name = "Events", description = "Event catalog, lifecycle, search, pricing")
public class EventController {

    private final IEventManagementService eventService;
    private final IAuth auth;
    private final UserDomainService userDomainService;

    public EventController(IEventManagementService eventService, IAuth auth, UserDomainService userDomainService) {
        this.eventService = eventService;
        this.auth = auth;
        this.userDomainService = userDomainService;
    }

    @Operation(summary = "Create a new event in DRAFT state")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<UUID>> createEvent(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody CreateEventCommand cmd) {
        try {
            UUID eventId = eventService.createEvent(cmd, token);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(eventId, null));
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

    @Operation(summary = "Get a single event with areas and seat statuses")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventDTO>> getEvent(
            @PathVariable UUID eventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        try {
            EventDTO event = eventService.getEvent(eventId);

            if (event != null && event.status() == com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus.DRAFT) {
                if (token == null || token.isBlank()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<>(null, "event not found: " + eventId));
                }
                if (!auth.isTokenValid(token)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ApiResponse<>(null, "Invalid or expired token"));
                }
                if (!auth.isMember(token)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(new ApiResponse<>(null, "Only members can view draft events"));
                }

                UUID callerId = auth.extractUserId(token);
                UUID companyId = eventService.getCompanyIdForEventId(eventId);

                boolean allowed = userDomainService.isAssignedManager(callerId, eventId, companyId);
                if (!allowed) {
                    for (ManagerPermission p : ManagerPermission.values()) {
                        try {
                            userDomainService.isLegalEventManager(eventId, callerId, companyId, p);
                            allowed = true;
                            break;
                        } catch (RuntimeException ignored) {
                            // try next permission
                        }
                    }
                }

                if (!allowed) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<>(null, "event not found: " + eventId));
                }
            }

            return ResponseEntity.ok(new ApiResponse<>(event, null));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Patch descriptive fields of an event (null = unchanged)")
    @PatchMapping(path = "/{eventId}", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> updateEvent(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody UpdateEventCommand cmd) {
        try {
            eventService.updateEvent(eventId, cmd, token);
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

    @Operation(summary = "Publish a DRAFT event")
    @PostMapping("/{eventId}/publish")
    public ResponseEntity<ApiResponse<Void>> publish(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        try {
            eventService.publish(eventId, token);
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

    @Operation(summary = "Cancel an event (idempotent)")
    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        try {
            eventService.cancel(eventId, token);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (PolicyViolationException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Get event-level availability (AVAILABLE / SOLD_OUT / INACTIVE)")
    @GetMapping("/{eventId}/availability")
    public ResponseEntity<ApiResponse<EventAvailabilityDTO>> getAvailability(@PathVariable UUID eventId) {
        try {
            EventAvailabilityDTO availability = eventService.getEventAvailability(eventId);
            return ResponseEntity.ok(new ApiResponse<>(availability, null));
        } catch (InvalidEventStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Search the global catalog. Use an empty body {} to match all.")
    @PostMapping(path = "/search", consumes = "application/json")
    public ResponseEntity<ApiResponse<List<EventDTO>>> search(@RequestBody(required = false) SearchCriteria criteria) {
        try {
            List<EventDTO> result = eventService.search(criteria == null ? SearchCriteria.empty() : criteria);
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Compute price quote for an order line")
    @PostMapping(path = "/{eventId}/price-quote", consumes = "application/json")
    public ResponseEntity<ApiResponse<PriceBreakdownDTO>> getPriceQuote(
            @PathVariable UUID eventId,
            @RequestBody PriceQuery query) {
        try {
            PriceBreakdownDTO price = eventService.getPrice(eventId, query);
            return ResponseEntity.ok(new ApiResponse<>(price, null));
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

    @Operation(summary = "Validate a purchase request against event purchase policies")
    @PostMapping(path = "/{eventId}/eligibility", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> validateEligibility(
            @PathVariable UUID eventId,
            @RequestBody PurchaseRequest request) {
        try {
            eventService.validatePurchaseEligibility(eventId, request);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (PolicyViolationException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ApiResponse<>(null, ex.getMessage()));
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
