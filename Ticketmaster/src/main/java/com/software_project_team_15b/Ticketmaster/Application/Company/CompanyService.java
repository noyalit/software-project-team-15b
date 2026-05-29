package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
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

    public List<CompanyDTO> findCompaniesByFounder(String token, UUID founderId) {
        requireValidToken(token);
        requireNonNull(founderId, "Founder ID");
        return companyDomainService.findCompaniesByFounder(founderId).stream()
                .map(CompanyDTO::from)
                .toList();
    }

    public CompanyDTO updatePurchasePolicy(String token, UUID companyId, ICompanyPurchasePolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Purchase policy");
            UUID callerId = requireAuthenticatedMember(token);
            if (!userDomainService.isActiveOwner(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only active owners can update the purchase policy");
            }
            var saved = companyDomainService.updatePurchasePolicy(companyId, policy);
            AUDIT.info("op=updatePurchasePolicy callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updatePurchasePolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    public CompanyDTO updateDiscountPolicy(String token, UUID companyId, ICompanyDiscountPolicy policy) {
        try {
            requireNonNull(companyId, "Company ID");
            requireNonNull(policy, "Discount policy");
            UUID callerId = requireAuthenticatedMember(token);
            if (!userDomainService.isActiveOwner(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only active owners can update the discount policy");
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
            eventDomainService.searchInCompany(companyId, null)
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
            eventDomainService.searchInCompany(companyId, null)
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
            AUDIT.info("op=closeCompany callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
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
     * @return the updated company with status {@link CompanyStatus#ACTIVE}
     * @throws InvalidTokenException             if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller lacks permission for the current status
     * @throws IllegalArgumentException          if the company is already active
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException
     *                                           if no company with {@code companyId} exists
     */
    public CompanyDTO activateCompany(String token, UUID companyId) {
        try {
            requireNonNull(companyId, "Company ID");
            requireValidToken(token);
            Company company = companyDomainService.getCompany(companyId, true);
            CompanyStatus currentStatus = company.getStatus();
            if (currentStatus == CompanyStatus.ACTIVE) {
                throw new IllegalArgumentException("Company is already active");
            }
            UUID callerId = auth.isMember(token) ? auth.extractUserId(token) : null;
            if (currentStatus == CompanyStatus.SUSPENDED) {
                if (!auth.isSystemAdmin(token)) {
                    throw new UnauthorizedCompanyActionException("Only system admins can reactivate a suspended company");
                }
            } else {
                if (!userDomainService.isActiveFounder(callerId, companyId)) {
                    throw new UnauthorizedCompanyActionException("Only a founder can reactivate a closed company");
                }
            }
            Company saved = companyDomainService.changeStatus(companyId, CompanyStatus.ACTIVE);
            AUDIT.info("op=activateCompany callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=activateCompany companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

    public CompanyDTO getCompany(String token, UUID companyId) {
        requireNonNull(token, "Token");
        requireNonNull(companyId, "Company ID");
        UUID callerId = auth.extractUserId(token);
        boolean canViewClosed = auth.isSystemAdmin(token)
                || userDomainService.isActiveFounder(callerId, companyId)
                || userDomainService.isActiveOwner(callerId, companyId);
        return CompanyDTO.from(companyDomainService.getCompany(companyId, canViewClosed));
    }

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