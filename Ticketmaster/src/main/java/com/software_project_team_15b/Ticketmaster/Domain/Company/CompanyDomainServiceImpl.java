package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CompanyDomainServiceImpl implements ICompanyDomainService {

    private final ICompanyRepository companyRepository;

    public CompanyDomainServiceImpl(ICompanyRepository companyRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository, "companyRepository cannot be null");
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
    @Transactional(readOnly = true)
    public List<Company> findCompaniesByOwner(UUID ownerId) {
        List<Company> result = companyRepository.findByOwner(ownerId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByOwner; expected an empty list");
        }
        return result;
    }

    @Override
    @Transactional
    public Company updatePurchasePolicy(UUID companyId, UUID callerId, ICompanyPurchasePolicy policy) {
        Company company = getCompanyOrThrow(companyId);
        company.updatePurchasePolicy(policy);
        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public Company updateDiscountPolicy(UUID companyId, UUID callerId, ICompanyDiscountPolicy policy) {
        Company company = getCompanyOrThrow(companyId);
        company.updateDiscountPolicy(policy);
        return companyRepository.save(company);
    }

    @Override
    public Company changeStatus(UUID companyId, UUID callerId, boolean isSystemAdmin, CompanyStatus newStatus) {
        return null;
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
}