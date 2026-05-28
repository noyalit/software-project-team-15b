package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CompanyDomainServiceImpl implements ICompanyDomainService {

    private final ICompanyRepository companyRepository;
    private final UserDomainService userDomainService;
    private final IEventDomainService eventDomainService;

    public CompanyDomainServiceImpl(ICompanyRepository companyRepository,
                                    UserDomainService userDomainService,
                                    IEventDomainService eventDomainService) {
        this.companyRepository = Objects.requireNonNull(companyRepository, "companyRepository cannot be null");
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService cannot be null");
        this.eventDomainService = Objects.requireNonNull(eventDomainService, "eventDomainService cannot be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Money cheapestPriceFor(UUID companyId, Money subtotal, PurchaseRequest request) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        if (subtotal == null) {
            throw new IllegalArgumentException("subtotal cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        Optional<Company> opt = companyRepository.findById(companyId);
        if (opt.isEmpty()) return subtotal;
        Company company = opt.get();
        Money best = subtotal;
        for (ICompanyDiscountPolicy policy : company.getDiscountPolicies()) {
            Money candidate = policy.apply(subtotal, request);
            if (candidate != null && candidate.amount().compareTo(best.amount()) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    @Override
    @Transactional(readOnly = true)
    public void validatePurchaseEligibility(UUID companyId, PurchaseRequest request) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        Optional<Company> opt = companyRepository.findById(companyId);
        if (opt.isEmpty()) return;
        Company company = opt.get();
        for (ICompanyPurchasePolicy policy : company.getPurchasePolicies()) {
            policy.validate(request, company);
        }
    }

    @Override
    @Transactional
    public Company createCompany(String name, UUID founderId) {
        Company company = new Company(name, founderId);
        company = companyRepository.save(company);
        userDomainService.appointFounder(founderId, company.getId());
        return company;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findCompaniesByFounder(UUID founderId) {
        List<Company> result = companyRepository.findByFounder(founderId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByFounder; expected an empty list");
        }
        return result;
    }

    @Override
    @Transactional
    public Company updatePurchasePolicy(UUID companyId, UUID callerId, ICompanyPurchasePolicy policy) {
        Company company = getCompanyOrThrow(companyId);
        requireOwner(company, callerId);
        company.updatePurchasePolicy(policy);
        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public Company updateDiscountPolicy(UUID companyId, UUID callerId, ICompanyDiscountPolicy policy) {
        Company company = getCompanyOrThrow(companyId);
        requireOwner(company, callerId);
        company.updateDiscountPolicy(policy);
        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public Company changeStatus(UUID companyId, UUID callerId, boolean isSystemAdmin, CompanyStatus newStatus) {
        Company company = getCompanyOrThrow(companyId);
        requireFounderOrSystemAdmin(company, callerId, isSystemAdmin);
        company.changeStatus(newStatus);
        if (newStatus == CompanyStatus.CLOSED) {
            eventDomainService.searchInCompany(companyId, null)
                    .forEach(event -> eventDomainService.cancel(event.eventId()));
        }
        return companyRepository.save(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Company getCompany(UUID companyId) {
        return getCompanyOrThrow(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findCompany(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId);
    }

    private Company getCompanyOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(
                        "Company not found with id: " + companyId));
    }

    private void requireOwner(Company company, UUID callerId) {
        UUID companyId = company.getId();
        if (!userDomainService.isActiveOwner(callerId, companyId) && !userDomainService.isActiveFounder(callerId, companyId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only an owner of the company can perform this action");
        }
    }

    private void requireFounderOrSystemAdmin(Company company, UUID callerId, boolean isSystemAdmin) {
        if (isSystemAdmin) return;
        UUID companyId = company.getId();
        if (callerId == null || !company.getFounderId().equals(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
        if (!userDomainService.isActiveFounder(callerId, companyId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
    }
}