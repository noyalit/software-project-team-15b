package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.*;

import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;

/**
 * Application-level service for managing {@link Company} aggregates.
 * Coordinates authentication, authorization, and persistence so callers
 * interact with companies through use-case methods rather than directly
 * with the domain model and repository.
 *
 * <p>Authorization rules enforced here:
 * <ul>
 *     <li>Creating a company requires an authenticated member; that member
 *         becomes the founder.</li>
 *     <li>Updating purchase or discount policies is restricted to the
 *         company's owners.</li>
 *     <li>Changing a company's status is restricted to the founder or a
 *         system administrator.</li>
 *     <li>Appointing or removing event managers for a specific event is
 *         restricted to the company's owners.</li>
 * </ul>
 */
@Service
public class CompanyService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.company");

    private final ICompanyRepository companyRepository;
    private final UserDomainService userDomainService;
    private final IEventDomainService eventManagementService;
    private final IAuth auth;

    /**
     * @param companyRepository      repository used to load and persist companies; must not be null
     * @param userDomainService      user domain service for role queries; must not be null
     * @param eventManagementService event management gateway; must not be null
     * @param auth                   authentication/authorization gateway; must not be null
     * @throws NullPointerException if any argument is null
     */
    public CompanyService(ICompanyRepository companyRepository, UserDomainService userDomainService, IEventDomainService eventManagementService, IAuth auth) {
        this.companyRepository = Objects.requireNonNull(companyRepository, "companyRepository cannot be null");
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService cannot be null");
        this.auth = Objects.requireNonNull(auth, "auth cannot be null");
        this.eventManagementService = Objects.requireNonNull(eventManagementService, "eventManagementService cannot be null");
    }

    /**
     * Creates a new company whose founder is the authenticated member.
     *
     * @param token an active member token; must not be null or blank
     * @param name  the company's display name; must not be null or blank
     * @return the newly persisted company
     * @throws IllegalArgumentException if {@code name} is null or blank
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    public Company createCompany(String token, String name) {
        try {
            requireNonBlank(name, "Company name");
            UUID founderId = requireAuthenticatedMember(token);
            Company company = new Company(name, founderId);
            company = companyRepository.save(company);
            userDomainService.appointFounder(founderId, company.getId());
            AUDIT.info("op=createCompany founderId={} companyId={} name={} result=ok",
                    founderId, company.getId(), name);
            return company;
        } catch (RuntimeException e) {
            AUDIT.warn("op=createCompany name={} result=rejected reason={}", name, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all companies whose founder matches {@code founderId}.
     *
     * <p>Note: {@code token} is accepted for API consistency but no
     * authorization check is currently enforced on this query.
     *
     * @param token     caller's token (currently unused for authorization)
     * @param founderId the id of the founder to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     * @throws IllegalArgumentException if {@code founderId} is null
     * @throws IllegalStateException    if the repository returns null instead of an empty list
     */
    public List<Company> findCompaniesByFounder(String token, UUID founderId) {
        requireNonNull(founderId, "Founder ID");
        List<Company> result = companyRepository.findByFounder(founderId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByFounder; expected an empty list");
        }
        return result;
    }

    /**
     * Replaces the company's purchase policy. Only an owner of the company
     * may perform this action, and the company must be in an active state.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code policy} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException if the company is not active
     */
    public Company updatePurchasePolicy(String token, UUID companyId, ICompanyPurchasePolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Purchase policy");
            UUID callerId = requireAuthenticatedMember(token);
            Company company = getCompanyOrThrow(companyId);
            requireOwner(company, callerId);
            company.updatePurchasePolicy(policy);
            Company saved = companyRepository.save(company);
            AUDIT.info("op=updatePurchasePolicy callerId={} companyId={} result=ok", callerId, companyId);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("op=updatePurchasePolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Replaces the company's discount policy. Only an owner of the company
     * may perform this action, and the company must be in an active state.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code policy} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException if the company is not active
     */
    public Company updateDiscountPolicy(String token, UUID companyId, ICompanyDiscountPolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Discount policy");
            UUID callerId = requireAuthenticatedMember(token);
            Company company = getCompanyOrThrow(companyId);
            requireOwner(company, callerId);
            company.updateDiscountPolicy(policy);
            Company saved = companyRepository.save(company);
            AUDIT.info("op=updateDiscountPolicy callerId={} companyId={} result=ok", callerId, companyId);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateDiscountPolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Transitions the company to the given status. Only the founder of the
     * company or a system administrator may perform this action.
     *
     * @param token     an active member or system-admin token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param newStatus the status to transition to; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code newStatus} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is neither the founder nor a system admin
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public Company changeStatus(String token, UUID companyId, CompanyStatus newStatus) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(newStatus, "Company status");
            Company company = getCompanyOrThrow(companyId);
            requireFounderOrSystemAdmin(token, company);
            company.changeStatus(newStatus);
            if (newStatus == CompanyStatus.CLOSED) {
                eventManagementService.searchInCompany(companyId, null)
                        .forEach(event -> eventManagementService.cancel(event.eventId()));
            }
            Company saved = companyRepository.save(company);
            AUDIT.info("op=changeStatus companyId={} newStatus={} result=ok", companyId, newStatus);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("op=changeStatus companyId={} newStatus={} result=rejected reason={}",
                    companyId, newStatus, e.getMessage());
            throw e;
        }
    }

    /**
     * Loads a company by id, failing if it does not exist.
     *
     * @param companyId the company's id; must not be null
     * @return the company with the given id
     * @throws IllegalArgumentException if {@code companyId} is null
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public Company getCompany(UUID companyId) {
        requireNonNull(companyId, "Company ID");
        return getCompanyOrThrow(companyId);
    }

    /**
     * Looks up a company by id without throwing when absent.
     *
     * @param companyId the company's id; may be null
     * @return an {@link Optional} containing the company if found, or empty when the id is null
     */
    public Optional<Company> findCompany(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId);
    }

    /**
     * @param companyId the company's id; must not be null
     * @return the company with the given id
     * @throws CompanyNotFoundException if no matching company exists
     */
    private Company getCompanyOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(
                        "Company not found with id: " + companyId));
    }

    /**
     * @param company  the target company; must not be null
     * @param callerId the id of the caller to authorize; must not be null
     * @throws UnauthorizedCompanyActionException if {@code callerId} is not in the company's owner set,
     *         or is neither an active owner nor an active founder in {@code UserService}
     */
    private void requireOwner(Company company, UUID callerId) {
        // isActiveOwner excludes Founders; check isActiveFounder as well so that
        // the company founder can exercise owner-level actions.
        if (!userDomainService.isActiveOwner(callerId) && !userDomainService.isActiveFounder(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only an owner of the company can perform this action");
        }
    }

    /**
     * Authorizes the caller as either the company's founder or a system admin.
     *
     * @param token   the caller's token; must not be null or blank
     * @param company the target company; must not be null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is neither the founder nor a system admin
     */
    private void requireFounderOrSystemAdmin(String token, Company company) {
        requireValidToken(token);
        if (auth.isSystemAdmin(token)) {
            return;
        }
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
        UUID callerId = auth.extractUserId(token);
        if (!company.getFounderId().equals(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
        if (!userDomainService.isActiveFounder(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
    }

    /**
     * Authorizes the caller as a member and extracts their user id.
     *
     * @param token the caller's token; must not be null or blank
     * @return the authenticated member's user id
     * @throws InvalidTokenException if the token is null, blank, not valid, or carries no user id
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    private UUID requireAuthenticatedMember(String token) {
        requireValidToken(token);
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException(
                    "Only members can perform this action");
        }
        UUID userId = auth.extractUserId(token);
        if (userId == null) {
            throw new InvalidTokenException("Token does not contain a valid user id");
        }
        return userId;
    }

    /**
     * @param token the token to verify; must not be null or blank
     * @throws InvalidTokenException if the token is null, blank, expired, or otherwise not valid
     */
    private void requireValidToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token cannot be null or blank");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    /**
     * @param value     the value to check
     * @param fieldName label used in the exception message
     * @throws IllegalArgumentException if {@code value} is null
     */
    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    /**
     * @param value     the string to check
     * @param fieldName label used in the exception message
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
