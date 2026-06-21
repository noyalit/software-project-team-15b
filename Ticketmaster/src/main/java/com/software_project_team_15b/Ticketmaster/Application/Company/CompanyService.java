package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;

/**
 * Application-level facade for {@link Company} use cases.
 * Validates tokens and resolves caller identity, then delegates all business
 * logic to {@link ICompanyDomainService}.
 */
@Service
public class CompanyService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.company");

    private final ICompanyDomainService companyDomainService;
    private final UserDomainService userDomainService;
    private final IEventDomainService eventDomainService;
    private final IAuth auth;

    public CompanyService(ICompanyDomainService companyDomainService, UserDomainService userDomainService, IEventDomainService eventDomainService, IAuth auth) {
        this.companyDomainService = Objects.requireNonNull(companyDomainService, "companyDomainService cannot be null");
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService cannot be null");
        this.eventDomainService = Objects.requireNonNull(eventDomainService, "eventDomainService cannot be null");
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
        return createCompany(token, name, null, null);
    }

    /**
     * Creates a new production company with optional initial policies.
     * Behaves like {@link #createCompany(String, String)} but additionally sets the company's
     * purchase and/or discount policy immediately after creation when non-null arguments are provided.
     *
     * @param token          the caller's member session token
     * @param name           the desired company name (must not be null or blank)
     * @param purchasePolicy an initial purchase policy, or {@code null} to leave unset
     * @param discountPolicy an initial discount policy, or {@code null} to leave unset
     * @return a {@link CompanyDTO} representing the newly created company
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is invalid, expired, or belongs to a non-member
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     * @throws IllegalArgumentException          if {@code name} is null or blank
     */
    public CompanyDTO createCompany(
            String token,
            String name,
            ICompanyPurchasePolicy purchasePolicy,
            ICompanyDiscountPolicy discountPolicy) {
        try {
            requireNonBlank(name, "Company name");
            UUID founderId = requireAuthenticatedMember(token);
            var company = companyDomainService.createCompany(name, founderId);

            if (purchasePolicy != null) {
                company = companyDomainService.updatePurchasePolicy(company.getId(), purchasePolicy);
            }
            if (discountPolicy != null) {
                company = companyDomainService.updateDiscountPolicy(company.getId(), discountPolicy);
            }

            userDomainService.appointFounder(founderId, company.getId());
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
     * Returns all companies in which the authenticated caller is either the founder or an owner.
     *
     * @param token the caller's member session token; must not be null or blank
     * @return a (possibly empty) deduplicated list of {@link CompanyDTO}s for the caller's companies
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    public List<CompanyDTO> getMyCompanies(String token) {
        requireValidToken(token);
        UUID memberId = requireAuthenticatedMember(token);
        return companyDomainService.findCompaniesByMember(memberId).stream()
                .map(CompanyDTO::from)
                .toList();
    }

    /**
     * Returns every company in the system. Only a system admin may call this method.
     *
     * @param token a valid system-admin session token; must not be null or blank
     * @return a (possibly empty) list of all {@link CompanyDTO}s
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws UnauthorizedCompanyActionException if the caller is not a system admin
     */
    public List<CompanyDTO> getAllCompanies(String token) {
        requireValidToken(token);
        if (!auth.isSystemAdmin(token)) {
            throw new UnauthorizedCompanyActionException("Only system admins can view all companies");
        }
        return companyDomainService.findAll().stream()
                .map(CompanyDTO::from)
                .toList();
    }

    /**
     * Replaces the purchase policy of the specified company.
     * The caller must be an active founder or owner of the company, or an approved manager who holds
     * the purchase-policy permission for that company.
     *
     * @param token     the caller's member session token
     * @param companyId the UUID of the target company
     * @param policy    the new purchase policy to apply (must not be null)
     * @return a {@link CompanyDTO} reflecting the updated state
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is invalid or expired
     * @throws UnauthorizedCompanyActionException if the caller lacks the required role or permission
     * @throws IllegalArgumentException          if {@code companyId} or {@code policy} is null
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
            var saved = companyDomainService.updatePurchasePolicy(companyId, policy);
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
            var saved = companyDomainService.updateDiscountPolicy(companyId, policy);
            AUDIT.info("op=updateDiscountPolicy callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateDiscountPolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Suspends the company and cancels all its events. Only a system admin may perform this action.
     *
     * @param token     a valid system-admin token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @return the updated company with status {@link CompanyStatus#SUSPENDED}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not a system admin
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public CompanyDTO suspendCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            if (!auth.isSystemAdmin(token)) {
                throw new UnauthorizedCompanyActionException("Only system admins can suspend companies");
            }
            Company saved = companyDomainService.changeStatus(companyId, CompanyStatus.SUSPENDED);
            eventDomainService.searchInCompany(companyId, SearchCriteria.empty())
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
            UUID callerId = auth.extractUserId(token);
            AUDIT.info("op=suspendCompany callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=suspendCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Closes the company and cancels all its events. Only the company's founder may perform this action.
     *
     * @param token     a valid member token for the founder; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @return the updated company with status {@link CompanyStatus#CLOSED}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not the founder
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public CompanyDTO closeCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            UUID callerId = auth.extractUserId(token);
            if (!userDomainService.isActiveFounder(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only company founder can close the company");
            }
            Company saved = companyDomainService.changeStatus(companyId, CompanyStatus.CLOSED);
            eventDomainService.searchInCompany(companyId, SearchCriteria.empty())
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
            AUDIT.info("op=closeCompany callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=closeCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Reactivates a closed company. The caller must be the company's active founder.
     * <ul>
     *   <li>A {@link CompanyStatus#CLOSED} company may only be reopened by its active founder.</li>
     *   <li>A {@link CompanyStatus#SUSPENDED} company cannot be reactivated through this method;
     *       the domain state machine does not permit {@code SUSPENDED → ACTIVE}.</li>
     *   <li>An {@link CompanyStatus#ACTIVE} company cannot be activated again.</li>
     * </ul>
     *
     * @param token     a valid member token for the founder; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @return the updated company with status {@link CompanyStatus#ACTIVE}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not the active founder
     * @throws IllegalStateException             if the company is not in {@link CompanyStatus#CLOSED} state
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public CompanyDTO activateCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            UUID callerId = auth.isMember(token) ? auth.extractUserId(token) : null;
            if (!userDomainService.isActiveFounder(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only a founder can reactivate a closed company");
            }
            Company saved = companyDomainService.changeStatus(companyId, CompanyStatus.ACTIVE);
            AUDIT.info("op=activateCompany callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=activateCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves the company with the given id. The caller must supply a valid token.
     * Closed and suspended companies are visible only to system admins, the company's
     * active founder, or its active owners.
     *
     * @param token     a valid session token; must not be null
     * @param companyId the UUID of the target company; must not be null
     * @return the {@link CompanyDTO} for the requested company
     * @throws IllegalArgumentException          if {@code token} or {@code companyId} is null
     * @throws UnauthorizedCompanyActionException if the company is closed/suspended and the caller
     *                                            lacks the required role
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public CompanyDTO getCompany(String token, UUID companyId) {
        requireNonNull(token, "Token");
        requireNonNull(companyId, "Company ID");
        UUID callerId = auth.extractUserId(token);
        boolean canViewClosed = auth.isSystemAdmin(token)
                || userDomainService.isActiveFounder(callerId, companyId)
                || userDomainService.isActiveOwner(callerId, companyId);
        return CompanyDTO.from(companyDomainService.getCompany(companyId, canViewClosed));
    }

    /**
     * Returns the current purchase policies of the specified company.
     * Closed and suspended companies are visible only to system admins, the company's
     * active founder, or its active owners.
     *
     * @param token     a valid session token; must not be null or blank
     * @param companyId the UUID of the target company; must not be null
     * @return an unmodifiable list of {@link ICompanyPurchasePolicy} instances, possibly empty
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws IllegalArgumentException          if {@code companyId} is null
     * @throws UnauthorizedCompanyActionException if the company is closed/suspended and the caller
     *                                            lacks the required role
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public List<ICompanyPurchasePolicy> getCompanyPurchasePolicies(String token, UUID companyId) {
        requireValidToken(token);
        requireNonNull(companyId, "Company ID");
        UUID callerId = auth.extractUserId(token);
        boolean canViewClosed = auth.isSystemAdmin(token)
                || userDomainService.isActiveFounder(callerId, companyId)
                || userDomainService.isActiveOwner(callerId, companyId);
        Company company = companyDomainService.getCompany(companyId, canViewClosed);
        return company.getPurchasePolicies();
    }

    /**
     * Returns the current discount policies of the specified company.
     * Closed and suspended companies are visible only to system admins, the company's
     * active founder, or its active owners.
     *
     * @param token     a valid session token; must not be null or blank
     * @param companyId the UUID of the target company; must not be null
     * @return an unmodifiable list of {@link ICompanyDiscountPolicy} instances, possibly empty
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException
     *         if the token is null, blank, or invalid
     * @throws IllegalArgumentException          if {@code companyId} is null
     * @throws UnauthorizedCompanyActionException if the company is closed/suspended and the caller
     *                                            lacks the required role
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *         if no company exists with the given {@code companyId}
     */
    public List<ICompanyDiscountPolicy> getCompanyDiscountPolicies(String token, UUID companyId) {
        requireValidToken(token);
        requireNonNull(companyId, "Company ID");
        UUID callerId = auth.extractUserId(token);
        boolean canViewClosed = auth.isSystemAdmin(token)
                || userDomainService.isActiveFounder(callerId, companyId)
                || userDomainService.isActiveOwner(callerId, companyId);
        Company company = companyDomainService.getCompany(companyId, canViewClosed);
        return company.getDiscountPolicies();
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