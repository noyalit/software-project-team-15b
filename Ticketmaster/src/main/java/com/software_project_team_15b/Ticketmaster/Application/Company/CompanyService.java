package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;

/**
 * Application-level facade for company use cases.
 * Validates tokens and resolves caller identity, then delegates all business
 * logic to {@link ICompanyDomainService}.
 */
@Service
public class CompanyService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.company");

    private final ICompanyDomainService companyDomainService;
    private final UserDomainService userDomainService;
    private final IAuth auth;

    public CompanyService(ICompanyDomainService companyDomainService, UserDomainService userDomainService, IAuth auth) {
        this.companyDomainService = Objects.requireNonNull(companyDomainService, "companyDomainService cannot be null");
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService cannot be null");
        this.auth = Objects.requireNonNull(auth, "auth cannot be null");
    }

    /**
     * Creates a new production company whose founder is the authenticated caller.
     *
     * @param token the caller's member session token
     * @param name  the desired company name (must not be null or blank)
     * @return a {@link CompanyDTO} representing the newly created company
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is invalid, expired, or belongs to a non-member
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     * @throws IllegalArgumentException           if {@code name} is null or blank
     */
    public CompanyDTO createCompany(String token, String name) {
        try {
            requireNonBlank(name, "Company name");
            UUID founderId = requireAuthenticatedMember(token);
            var company = companyDomainService.createCompany(name, founderId);
            AUDIT.info("op=createCompany founderId={} companyId={} name={} result=ok",
                    founderId, company.getId(), name);
            return CompanyDTO.from(company);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createCompany name={} result=rejected reason={}", name, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all companies whose founder matches the given {@code founderId}.
     *
     * @param token     a valid session token (any user type)
     * @param founderId the UUID of the founder to query
     * @return a (possibly empty) list of matching {@link CompanyDTO}s
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws IllegalArgumentException if {@code founderId} is null
     */
    public List<CompanyDTO> findCompaniesByFounder(String token, UUID founderId) {
        requireValidToken(token);
        requireNonNull(founderId, "Founder ID");
        return companyDomainService.findCompaniesByFounder(founderId).stream()
                .map(CompanyDTO::from)
                .toList();
    }

    /**
     * Replaces the purchase policy of the specified company.
     * The caller must be an active founder or owner of the company, or an approved manager who holds
     * the {@link com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission#DEFINE_PURCHASE_POLICY}
     * permission for that company.
     *
     * @param token     the caller's member session token
     * @param companyId the UUID of the target company
     * @param policy    the new purchase policy to apply (must not be null)
     * @return a {@link CompanyDTO} reflecting the updated state
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is invalid or expired
     * @throws UnauthorizedCompanyActionException if the caller lacks the required role or permission
     * @throws IllegalArgumentException           if {@code companyId} or {@code policy} is null
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public CompanyDTO updatePurchasePolicy(String token, UUID companyId, ICompanyPurchasePolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Purchase policy");
            UUID callerId = requireAuthenticatedMember(token);
            boolean isOwner = userDomainService.isActiveOwner(callerId, companyId) || userDomainService.isActiveFounder(callerId, companyId);
            if (!isOwner && !userDomainService.canChangePurchasePolicy(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("User does not have permission to change purchase policy");
            }
            var saved = companyDomainService.updatePurchasePolicy(companyId, callerId, policy);
            AUDIT.info("op=updatePurchasePolicy callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updatePurchasePolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Replaces the discount policy of the specified company.
     * The caller must be an active founder or owner of the company, or an approved manager who holds
     * the {@link com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission#DEFINE_DISCOUNT_POLICY}
     * permission for that company.
     *
     * @param token     the caller's member session token
     * @param companyId the UUID of the target company
     * @param policy    the new discount policy to apply (must not be null)
     * @return a {@link CompanyDTO} reflecting the updated state
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is invalid or expired
     * @throws UnauthorizedCompanyActionException if the caller lacks the required role or permission
     * @throws IllegalArgumentException           if {@code companyId} or {@code policy} is null
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public CompanyDTO updateDiscountPolicy(String token, UUID companyId, ICompanyDiscountPolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Discount policy");
            UUID callerId = requireAuthenticatedMember(token);
            boolean isOwner = userDomainService.isActiveOwner(callerId, companyId) || userDomainService.isActiveFounder(callerId, companyId);
            if (!isOwner && !userDomainService.canChangeDiscountPolicy(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("User does not have permission to change discount policy");
            }
            var saved = companyDomainService.updateDiscountPolicy(companyId, callerId, policy);
            AUDIT.info("op=updateDiscountPolicy callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateDiscountPolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Changes the status of the specified company.
     * System admins may perform any valid status transition; company founders/owners may
     * perform member-level transitions as defined by
     * {@link com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyDomainService#changeStatus}.
     *
     * @param token     a valid session token (member or system-admin)
     * @param companyId the UUID of the target company
     * @param newStatus the desired new {@link CompanyStatus}
     * @return a {@link CompanyDTO} reflecting the updated state, or {@code null} if the domain
     *         service returns no result
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws IllegalArgumentException if {@code companyId} or {@code newStatus} is null
     */
    public CompanyDTO changeStatus(String token, UUID companyId, CompanyStatus newStatus) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(newStatus, "Company status");
            requireValidToken(token);
            boolean isSystemAdmin = auth.isSystemAdmin(token);
            UUID callerId = auth.isMember(token) ? auth.extractUserId(token) : null;
            var saved = companyDomainService.changeStatus(companyId, callerId, isSystemAdmin, newStatus);
            AUDIT.info("op=changeStatus callerId={} companyId={} newStatus={} result=ok", callerId, companyId, newStatus);
            return saved != null ? CompanyDTO.from(saved) : null;
        } catch (RuntimeException e) {
            AUDIT.warn("op=changeStatus companyId={} newStatus={} result=rejected reason={}",
                    companyId, newStatus, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns the company with the given ID.
     *
     * @param companyId the UUID of the company to retrieve
     * @return the matching {@link CompanyDTO}
     * @throws IllegalArgumentException if {@code companyId} is null
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public CompanyDTO getCompany(UUID companyId) {
        requireNonNull(companyId, "Company ID");
        return CompanyDTO.from(companyDomainService.getCompany(companyId));
    }

    /**
     * Looks up the company with the given ID without throwing if absent.
     *
     * @param companyId the UUID of the company to look up (may be null)
     * @return an {@link Optional} containing the {@link CompanyDTO}, or empty if not found
     */
    public Optional<CompanyDTO> findCompany(UUID companyId) {
        return companyDomainService.findCompany(companyId).map(CompanyDTO::from);
    }

    private UUID requireAuthenticatedMember(String token) {
        requireValidToken(token);
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException("Only members can perform this action");
        }
        UUID userId = auth.extractUserId(token);
        if (userId == null) {
            throw new InvalidTokenException("Token does not contain a valid user id");
        }
        return userId;
    }

    private void requireValidToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token cannot be null or blank");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}