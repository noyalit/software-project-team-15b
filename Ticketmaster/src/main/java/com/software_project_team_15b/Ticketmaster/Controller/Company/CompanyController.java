package com.software_project_team_15b.Ticketmaster.Controller.Company;

import java.util.List;
import java.util.UUID;

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

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
            CompanyDTO company = companyService.createCompany(
                    token,
                    request.name(),
                    request.purchasePolicy(),
                    request.discountPolicy()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(company, null));
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

    @Operation(summary = "List the company's purchase-policy chain in order")
    @GetMapping("/{companyId}/purchase-policies")
    public ResponseEntity<ApiResponse<List<ICompanyPurchasePolicy>>> getCompanyPurchasePolicies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(companyService.getCompanyPurchasePolicies(token, companyId), null));
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

    @Operation(summary = "List the company's discount-policy chain in order")
    @GetMapping("/{companyId}/discount-policies")
    public ResponseEntity<ApiResponse<List<ICompanyDiscountPolicy>>> getCompanyDiscountPolicies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(companyService.getCompanyDiscountPolicies(token, companyId), null));
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

    @Operation(summary = "List companies related to the logged in member")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> getMyCompanies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<CompanyDTO> result = companyService.getMyCompanies(token);
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
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            CompanyDTO company = companyService.getCompany(token, companyId);
            return ResponseEntity.ok(new ApiResponse<>(company, null));
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

    @Operation(summary = "List all companies (system admin only)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> getAllCompanies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<CompanyDTO> result = companyService.getAllCompanies(token);
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

    @Operation(summary = "Suspend a company and cancel its events (system admin only)")
    @PatchMapping("/{companyId}/suspend")
    public ResponseEntity<ApiResponse<CompanyDTO>> suspendCompany(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            CompanyDTO company = companyService.suspendCompany(token, companyId);
            return ResponseEntity.ok(new ApiResponse<>(company, null));
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

    @Operation(summary = "Close a company and cancel its events (founder only)")
    @PatchMapping("/{companyId}/close")
    public ResponseEntity<ApiResponse<CompanyDTO>> closeCompany(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            CompanyDTO company = companyService.closeCompany(token, companyId);
            return ResponseEntity.ok(new ApiResponse<>(company, null));
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

    @Operation(summary = "Reactivate a closed company (founder only)")
    @PatchMapping("/{companyId}/activate")
    public ResponseEntity<ApiResponse<CompanyDTO>> activateCompany(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            CompanyDTO company = companyService.activateCompany(token, companyId);
            return ResponseEntity.ok(new ApiResponse<>(company, null));
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

    public record CreateCompanyRequest(
            String name,
            ICompanyPurchasePolicy purchasePolicy,
            ICompanyDiscountPolicy discountPolicy
    ) {}

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