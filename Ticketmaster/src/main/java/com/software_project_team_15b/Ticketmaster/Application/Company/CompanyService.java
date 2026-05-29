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
            var saved = companyDomainService.updatePurchasePolicy(companyId, callerId, policy);
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
            var saved = companyDomainService.updateDiscountPolicy(companyId, callerId, policy);
            AUDIT.info("op=updateDiscountPolicy callerId={} companyId={} result=ok", callerId, companyId);
            return CompanyDTO.from(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateDiscountPolicy companyId={} result=rejected reason={}", companyId, e.getMessage());
            throw e;
        }
    }

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

    public CompanyDTO getCompany(UUID companyId) {
        requireNonNull(companyId, "Company ID");
        return CompanyDTO.from(companyDomainService.getCompany(companyId));
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