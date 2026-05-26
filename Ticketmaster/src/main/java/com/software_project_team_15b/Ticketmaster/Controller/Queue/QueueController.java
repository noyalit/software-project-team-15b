package com.software_project_team_15b.Ticketmaster.Controller.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueSnapshotDTO;

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
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/queues", produces = "application/json")
@Tag(name = "Queues", description = "Virtual queue management for events")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @Operation(summary = "Get all queue snapshots (admin only)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<QueueSnapshotDTO>>> getAllQueueSnapshots(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<QueueSnapshotDTO> snapshots = queueService.getAllQueueSnapshots(token);
            return ResponseEntity.ok(new ApiResponse<>(snapshots, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get queue snapshot for an event (admin only)")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<QueueSnapshotDTO>> getQueueSnapshot(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            QueueSnapshotDTO snapshot = queueService.getQueueSnapshot(token, eventId);
            return ResponseEntity.ok(new ApiResponse<>(snapshot, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (QueueNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Create a virtual queue for an event (admin only)")
    @PostMapping(path = "/{eventId}", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> createEventQueue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId,
            @RequestBody QueueSettingsRequest request
    ) {
        try {
            queueService.createEventQueue(token, eventId, request.capacity(), request.maxAccepted());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Delete the virtual queue for an event (admin only)")
    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEventQueue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            queueService.deleteEventQueue(token, eventId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (QueueNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Update capacity and max-accepted limits for an event queue (admin only)")
    @PatchMapping(path = "/{eventId}", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> updateEventQueueSettings(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId,
            @RequestBody QueueSettingsRequest request
    ) {
        try {
            queueService.updateEventQueueSettings(token, eventId, request.capacity(), request.maxAccepted());
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (QueueNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove all users from the event queue (admin only)")
    @DeleteMapping("/{eventId}/users")
    public ResponseEntity<ApiResponse<Void>> clearEventQueue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            queueService.clearEventQueue(token, eventId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (QueueNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get the caller's queue access state for an event")
    @GetMapping("/{eventId}/access")
    public ResponseEntity<ApiResponse<QueueAccessDTO>> getQueueAccessView(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            QueueAccessDTO view = queueService.getQueueAccessView(token, eventId);
            return ResponseEntity.ok(new ApiResponse<>(view, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (QueueNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    public record QueueSettingsRequest(int capacity, int maxAccepted) {}

    private <T> ResponseEntity<ApiResponse<T>> badRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> notFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> internalServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "Internal server error"));
    }
}