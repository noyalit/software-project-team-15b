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

    @Operation(summary = "List all companies (system admin only)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> listAllCompanies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<CompanyDTO> result = companyService.listAllCompanies(token).stream()
                    .map(CompanyDTO::from)
                    .toList();
            return ResponseEntity.ok(new ApiResponse<>(result, null));
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

    @Operation(summary = "List companies related to the logged in member")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> getMyCompanies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<CompanyDTO> result = companyService.getMyCompanies(token).stream()
                    .map(CompanyDTO::from)
                    .toList();
            return ResponseEntity.ok(new ApiResponse<>(result, null));
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

    public record CreateCompanyRequest(String name) {}

    public record ChangeStatusRequest(CompanyStatus status) {}

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
        String msg = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "The request failed due to a server error. Please try again later."
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, msg));
    }
}
