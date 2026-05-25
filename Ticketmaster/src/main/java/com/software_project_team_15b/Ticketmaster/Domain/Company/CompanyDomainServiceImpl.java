package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CompanyDomainServiceImpl implements ICompanyDomainService {
    private final ICompanyRepository companyRepository;

    public CompanyDomainServiceImpl(ICompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
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
}
