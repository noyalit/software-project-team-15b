package com.software_project_team_15b.Ticketmaster.Controller.Company;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/companies", produces = "application/json")
@Tag(name = "Companies", description = "Company lifecycle, ownership, and manager management")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Operation(summary = "Create a new company (caller becomes founder)")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<CompanyDTO>> createCompany(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody CreateCompanyRequest request
    ) {
        try {
            Company company = companyService.createCompany(token, request.name());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(CompanyDTO.from(company), null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get a company by ID")
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyDTO>> getCompany(
            @PathVariable UUID companyId
    ) {
        try {
            Company company = companyService.getCompany(companyId);
            return ResponseEntity.ok(new ApiResponse<>(CompanyDTO.from(company), null));
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Find companies by founder or owner (provide exactly one query param)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> findCompanies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam(required = false) UUID founderId,
            @RequestParam(required = false) UUID ownerId
    ) {
        try {
            if (founderId != null && ownerId != null) {
                return badRequest(new IllegalArgumentException("Provide either founderId or ownerId, not both"));
            }
            List<Company> companies;
            if (founderId != null) {
                companies = companyService.findCompaniesByFounder(token, founderId);
            } else if (ownerId != null) {
                companies = companyService.findCompaniesByOwner(token, ownerId);
            } else {
                return badRequest(new IllegalArgumentException("Provide either founderId or ownerId"));
            }
            List<CompanyDTO> result = companies.stream().map(CompanyDTO::from).toList();
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change the status of a company (founder or system admin only)")
    @PatchMapping(path = "/{companyId}/status", consumes = "application/json")
    public ResponseEntity<ApiResponse<CompanyDTO>> changeStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @RequestBody ChangeStatusRequest request
    ) {
        try {
            Company company = companyService.changeStatus(token, companyId, request.status());
            return ResponseEntity.ok(new ApiResponse<>(CompanyDTO.from(company), null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get all owner IDs for a company (owners only)")
    @GetMapping("/{companyId}/owners")
    public ResponseEntity<ApiResponse<Set<UUID>>> getOwnerIds(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            Set<UUID> ownerIds = companyService.getOwnerIds(token, companyId);
            return ResponseEntity.ok(new ApiResponse<>(ownerIds, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Add an owner to a company (owners only)")
    @PostMapping(path = "/{companyId}/owners", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> addOwner(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @RequestBody AddOwnerRequest request
    ) {
        try {
            companyService.addOwner(token, companyId, request.newOwnerId());
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove an owner from a company (owners only, founder cannot be removed)")
    @DeleteMapping("/{companyId}/owners/{ownerId}")
    public ResponseEntity<ApiResponse<Void>> removeOwner(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID ownerId
    ) {
        try {
            companyService.removeOwner(token, companyId, ownerId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Resign own ownership (owner resigns themselves)")
    @PostMapping("/{companyId}/owners/{userId}/resign")
    public ResponseEntity<ApiResponse<Void>> ownerResign(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID userId
    ) {
        try {
            companyService.ownerResign(token, companyId, userId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Add an event manager to a company event (owners only)")
    @PostMapping(path = "/{companyId}/events/{eventId}/managers", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> addEventManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId,
            @RequestBody AddManagerRequest request
    ) {
        try {
            companyService.addEventManager(token, companyId, eventId, request.userId(), request.permissions());
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove an event manager from a company event (owners only)")
    @DeleteMapping("/{companyId}/events/{eventId}/managers/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeEventManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId,
            @PathVariable UUID userId
    ) {
        try {
            companyService.removeEventManager(token, companyId, eventId, userId);
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Update a manager's permissions for a company event (owners only)")
    @PatchMapping(path = "/{companyId}/events/{eventId}/managers/{managerId}/permissions", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> updateManagerPermissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId,
            @PathVariable UUID eventId,
            @PathVariable UUID managerId,
            @RequestBody UpdatePermissionsRequest request
    ) {
        try {
            companyService.updateManagerPermissions(token, companyId, eventId, managerId, request.permissions());
            return ResponseEntity.ok(new ApiResponse<>(null, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (CompanyNotFoundException ex) {
            return notFound(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    public record CreateCompanyRequest(String name) {}

    public record ChangeStatusRequest(CompanyStatus status) {}

    public record AddOwnerRequest(UUID newOwnerId) {}

    public record AddManagerRequest(UUID userId, Set<ManagerPermission> permissions) {}

    public record UpdatePermissionsRequest(Set<ManagerPermission> permissions) {}

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

    private <T> ResponseEntity<ApiResponse<T>> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> internalServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "Internal server error"));
    }
}
