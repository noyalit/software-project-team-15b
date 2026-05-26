package com.software_project_team_15b.Ticketmaster.Controller.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryAlreadyDrawnException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.LotteryNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/companies/{companyId}/events/{eventId}/lottery", produces = "application/json")
@Tag(name = "Lottery", description = "Lottery lifecycle and participation for events")
public class LotteryController {

    private final LotteryService lotteryService;

    public LotteryController(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @Operation(summary = "Create a lottery for an event (manager/owner/founder only)")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createEventLottery(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId
    ) {
        try {
            lotteryService.createEventLottery(token, companyId, eventId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(null, null));
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Delete the lottery for an event (manager/owner/founder only)")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteEventLottery(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId
    ) {
        try {
            lotteryService.deleteEventLottery(token, companyId, eventId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (LotteryNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Enter the lottery for an event (members only)")
    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<Void>> addToEventLottery(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId
    ) {
        try {
            lotteryService.addToEventLottery(eventId, token);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(null, null));
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (LotteryNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Run the lottery draw for an event (manager/owner/founder only)")
    @PostMapping("/draw")
    public ResponseEntity<ApiResponse<Set<UUID>>> runEventLottery(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId,
            @RequestBody DrawRequest request
    ) {
        try {
            Set<UUID> winners = lotteryService.runEventLottery(token, companyId, eventId, request.count(), request.expirationTime());
            return ResponseEntity.ok(new ApiResponse<>(winners, null));
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (LotteryNotFoundException ex) {
            return notFound(ex);
        } catch (LotteryAlreadyDrawnException ex) {
            return conflict(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get the winners of the lottery for an event (manager/owner/founder only)")
    @GetMapping("/winners")
    public ResponseEntity<ApiResponse<Set<UUID>>> getEventLotteryWinners(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId
    ) {
        try {
            Set<UUID> winners = lotteryService.getEventLotteryWinners(token, companyId, eventId);
            return ResponseEntity.ok(new ApiResponse<>(winners, null));
        } catch (UnauthorizedException ex) {
            return forbidden(ex);
        } catch (LotteryNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get the caller's lottery eligibility for an event")
    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<LotteryEligibilityDTO>> getLotteryEligibility(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId
    ) {
        try {
            LotteryEligibilityDTO dto = lotteryService.getLotteryEligibilityForEvent(token, eventId);
            return ResponseEntity.ok(new ApiResponse<>(dto, null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    public record DrawRequest(int count, LocalDateTime expirationTime) {}

    private <T> ResponseEntity<ApiResponse<T>> badRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
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

    private <T> ResponseEntity<ApiResponse<T>> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> internalServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "Internal server error"));
    }
}