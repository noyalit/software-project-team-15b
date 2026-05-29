package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
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

    /**
     * Creates a new active company with the given name and founder, persists it, and returns it.
     *
     * @param name      the company's display name; must not be null
     * @param founderId the id of the founding member; must not be null
     * @return the newly persisted company
     * @throws IllegalArgumentException if {@code name} or {@code founderId} is null
     */
    @Override
    @Transactional
    public Company createCompany(String name, UUID founderId) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (founderId == null) {
            throw new IllegalArgumentException("founderId cannot be null");
        }
        Company company = new Company(name, founderId);
        company = companyRepository.save(company);
        return company;
    }

    /**
     * Returns all companies whose founder matches {@code founderId}.
     *
     * @param founderId the founder to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     * @throws IllegalArgumentException if {@code founderId} is null
     */
    @Override
    @Transactional(readOnly = true)
    public List<Company> findCompaniesByFounder(UUID founderId) {
        if (founderId == null) {
            throw new IllegalArgumentException("founderId cannot be null");
        }
        List<Company> result = companyRepository.findByFounder(founderId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByFounder; expected an empty list");
        }
        return result;
    }

    /**
     * Replaces the company's purchase policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param companyId the target company's id; must not be null
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException  if {@code companyId} or {@code policy} is null
     * @throws IllegalStateException     if the company is not active
     * @throws CompanyNotFoundException  if no company exists with the given id
     */
    @Override
    @Transactional
    public Company updatePurchasePolicy(UUID companyId, ICompanyPurchasePolicy policy) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy cannot be null");
        }
        Company company = getCompanyOrThrow(companyId);
        company.updatePurchasePolicy(policy);
        return companyRepository.save(company);
    }

    /**
     * Replaces the company's discount policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param companyId the target company's id; must not be null
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} or {@code policy} is null
     * @throws IllegalStateException    if the company is not active
     * @throws CompanyNotFoundException if no company exists with the given id
     */
    @Override
    @Transactional
    public Company updateDiscountPolicy(UUID companyId, ICompanyDiscountPolicy policy) {
        if  (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy cannot be null");
        }
        Company company = getCompanyOrThrow(companyId);
        company.updateDiscountPolicy(policy);
        return companyRepository.save(company);
    }

    /**
     * Transitions the company to the given status and persists the change.
     *
     * @param companyId the target company's id; must not be null
     * @param newStatus the status to transition to; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} or {@code newStatus} is null
     * @throws CompanyNotFoundException if no company exists with the given id
     */
    @Override
    @Transactional
    public Company changeStatus(UUID companyId, CompanyStatus newStatus) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus cannot be null");
        }

        Company company = getCompanyOrThrow(companyId);
        CompanyStatus currentStatus = company.getStatus();
        if (newStatus == CompanyStatus.CLOSED && currentStatus != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Company must be active to be closed");
        } else if (newStatus == CompanyStatus.SUSPENDED && currentStatus != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Company must be active to be suspended");
        } else if (newStatus == CompanyStatus.ACTIVE
                && currentStatus != CompanyStatus.CLOSED ){
            throw new IllegalStateException("Company must be closed to be reactivated");
        }

        company.changeStatus(newStatus);
        return companyRepository.save(company);
    }

    /**
     * Loads a company by id. Closed companies are only visible when {@code canViewClosed} is {@code true}.
     *
     * @param companyId     the company's id; must not be null
     * @param canViewClosed whether the caller is permitted to see closed companies
     *                      (pass {@code true} for admins, founders, and owners)
     * @return the company with the given id
     * @throws IllegalArgumentException          if {@code companyId} is null
     * @throws CompanyNotFoundException          if no company exists with the given id
     * @throws UnauthorizedCompanyActionException if the company is closed and
     *                                            {@code canViewClosed} is {@code false}
     */
    @Override
    @Transactional(readOnly = true)
    public Company getCompany(UUID companyId, boolean canViewClosed) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        Company company = getCompanyOrThrow(companyId);
        if (company.getStatus() == CompanyStatus.CLOSED && !canViewClosed) {
            throw new UnauthorizedCompanyActionException(
                    "Company with id " + companyId + " is closed and cannot be viewed");
        }
        return company;
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