package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;

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
     * Creates a new company whose founder is the authenticated member.
     *
     * @param token an active member token; must not be null or blank
     * @param name  the company's display name; must not be null or blank
     * @return the newly persisted company
     * @throws IllegalArgumentException          if {@code name} is null or blank
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    public Company createCompany(String token, String name) {
        try {
            requireNonBlank(name, "Company name");
            UUID founderId = requireAuthenticatedMember(token);
            Company company = companyDomainService.createCompany(name, founderId);
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
     * @param token     caller's token (validated but not used for authorization)
     * @param founderId the id of the founder to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     */
    public List<Company> findCompaniesByFounder(String token, UUID founderId) {
        requireValidToken(token);
        requireNonNull(founderId, "Founder ID");
        return companyDomainService.findCompaniesByFounder(founderId);
    }

    /**
     * Replaces the company's purchase policy. Only an owner of the company may perform this action.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     */
    public Company updatePurchasePolicy(String token, UUID companyId, ICompanyPurchasePolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Purchase policy");
            UUID callerId = requireAuthenticatedMember(token);
            if (!userDomainService.isActiveOwner(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only active owners can update the purchase policy");
            }
            Company saved = companyDomainService.updatePurchasePolicy(companyId, policy);
            AUDIT.info("op=updatePurchasePolicy callerId={} companyId={} result=ok", callerId, companyId);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("op=updatePurchasePolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Replaces the company's discount policy. Only an owner of the company may perform this action.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     */
    public Company updateDiscountPolicy(String token, UUID companyId, ICompanyDiscountPolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Discount policy");
            UUID callerId = requireAuthenticatedMember(token);
            if  (!userDomainService.isActiveOwner(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only active owners can update the discount policy");
            }
            Company saved = companyDomainService.updateDiscountPolicy(companyId, policy);
            AUDIT.info("op=updateDiscountPolicy callerId={} companyId={} result=ok", callerId, companyId);
            return saved;
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
     * @return the company with status {@link CompanyStatus#SUSPENDED}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not a system admin
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public Company suspendCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            if(!auth.isSystemAdmin(token)) {
                throw new UnauthorizedCompanyActionException("Only system admins can suspend companies");
            }
            companyDomainService.changeStatus(companyId, CompanyStatus.SUSPENDED);
            eventDomainService.searchInCompany(companyId, null)
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
            UUID callerId = auth.extractUserId(token);
            AUDIT.info("op=suspendCompany callerId={} companyId={} result=ok", callerId, companyId);
            return companyDomainService.getCompany(companyId);
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
     * @return the company with status {@link CompanyStatus#CLOSED}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not the founder
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public Company closeCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            UUID callerId = auth.extractUserId(token);
            if(!userDomainService.isActiveFounder(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only company founder can close the company");
            }
            companyDomainService.changeStatus(companyId, CompanyStatus.CLOSED);
            eventDomainService.searchInCompany(companyId, null)
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
            AUDIT.info("op=closeCompany callerId={} companyId={} result=ok", callerId, companyId);
            return companyDomainService.getCompany(companyId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=closeCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Reactivates a suspended or closed company.
     * <ul>
     *   <li>A {@link CompanyStatus#SUSPENDED} company may only be reactivated by a system admin.</li>
     *   <li>A {@link CompanyStatus#CLOSED} company may only be reopened by its founder.</li>
     *   <li>An {@link CompanyStatus#ACTIVE} company cannot be activated again.</li>
     * </ul>
     *
     * @param token     a valid token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @return the company with status {@link CompanyStatus#ACTIVE}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller lacks permission for the current status
     * @throws IllegalArgumentException          if the company is already active
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public Company activateCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            Company company = companyDomainService.getCompany(companyId);
            CompanyStatus currentStatus = company.getStatus();

            UUID callerId = auth.extractUserId(token);
            if(currentStatus == CompanyStatus.CLOSED && !userDomainService.isActiveFounder(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only company founder can reopen a closed company");
            } else if (currentStatus == CompanyStatus.SUSPENDED && !auth.isSystemAdmin(token)) {
                throw new  UnauthorizedCompanyActionException("Only system admins can reactivate companies");
            } else if (currentStatus  == CompanyStatus.ACTIVE) {
                throw new IllegalArgumentException("Company is already active");
            }
            companyDomainService.changeStatus(companyId, CompanyStatus.ACTIVE);
            AUDIT.info("op=activateCompany callerId={} companyId={} result=ok", callerId, companyId);
            return companyDomainService.getCompany(companyId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=activateCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    /**
     * Loads a company by id, failing if it does not exist.
     *
     * @param companyId the company's id; must not be null
     * @return the company with the given id
     */
    public Company getCompany(UUID companyId) {
        requireNonNull(companyId, "Company ID");
        return companyDomainService.getCompany(companyId);
    }

    /**
     * Looks up a company by id without throwing when absent.
     *
     * @param companyId the company's id; may be null
     * @return an {@link Optional} containing the company if found, or empty when the id is null
     */
    public Optional<Company> findCompany(UUID companyId) {
        return companyDomainService.findCompany(companyId);
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