package com.software_project_team_15b.Ticketmaster.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for the event's purchase- and discount-policy chains.
 *
 */
@RestController
@RequestMapping(path = "/api/events/{eventId}", produces = "application/json", consumes = "application/json")
@Tag(name = "Event Policies", description = "Replace purchase and discount policy chains")
public class EventPolicyController {

    private final IEventManagementService eventService;

    public EventPolicyController(IEventManagementService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "Replace the purchase-policy chain (empty list clears)")
    @PutMapping("/purchase-policies")
    public ResponseEntity<ApiResponse<Void>> replacePurchasePolicies(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody List<PurchasePolicyDTO> policies) {
        try {
            List<IEventPurchasePolicy> domainPolicies = policies.stream()
                    .map(PurchasePolicyDTO::toDomain)
                    .toList();
            eventService.replacePurchasePolicies(eventId, domainPolicies, token);
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
        } catch (IllegalArgumentException | NullPointerException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Replace the discount-policy chain (empty list clears)")
    @PutMapping("/discount-policies")
    public ResponseEntity<ApiResponse<Void>> replaceDiscountPolicies(
            @PathVariable UUID eventId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody List<DiscountPolicyDTO> policies) {
        try {
            List<IEventDiscountPolicy> domainPolicies = policies.stream()
                    .map(DiscountPolicyDTO::toDomain)
                    .toList();
            eventService.replaceDiscountPolicies(eventId, domainPolicies, token);
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
        } catch (IllegalArgumentException | NullPointerException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }
}
