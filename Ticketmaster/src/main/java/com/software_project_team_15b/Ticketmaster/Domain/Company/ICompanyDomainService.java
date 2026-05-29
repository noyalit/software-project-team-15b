package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICompanyDomainService {
    /**
     * Returns the cheapest price the company is willing to offer for the given subtotal.
     * Mirrors {@link com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy}
     * but for the company side. The returned amount is clamped to the subtotal so a misbehaving
     * policy cannot raise the price.
     *
     * @param companyId the owning company's id; must not be null
     * @param subtotal  the base subtotal in the buyer's currency; must not be null
     * @param request   the purchase request; must not be null
     * @return the lowest price the company offers, never above {@code subtotal}, in {@code subtotal}'s currency
     * @throws CompanyNotFoundException if the company does not exist
     */
    Money cheapestPriceFor(UUID companyId, Money subtotal, PurchaseRequest request);

    /**
     * Validates a purchase request against the company's own purchase policy.
     * Mirrors {@link com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy}
     * but for the company side of the transaction.
     *
     * @param companyId the owning company's id; must not be null
     * @param request   the purchase request; must not be null
     * @throws CompanyNotFoundException if the company does not exist
     */
    void validatePurchaseEligibility(UUID companyId, PurchaseRequest request);

    /**
     * Creates a new company and appoints the given member as its founder.
     *
     * @param name      the company's display name; must not be null or blank
     * @param founderId the id of the member becoming the founder; must not be null
     * @return the newly persisted company
     */
    Company createCompany(String name, UUID founderId);

    /**
     * Returns all companies whose founder matches {@code founderId}.
     *
     * @param founderId the id of the founder to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     */
    List<Company> findCompaniesByFounder(UUID founderId);

    /**
     * Replaces the company's purchase policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param companyId the target company's id; must not be null
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException    if the company is not active
     */
    Company updatePurchasePolicy(UUID companyId, ICompanyPurchasePolicy policy);

    /**
     * Replaces the company's discount policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param companyId the target company's id; must not be null
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException    if the company is not active
     */
    Company updateDiscountPolicy(UUID companyId, ICompanyDiscountPolicy policy);

    /**
     * Transitions the company to the given status and persists the change.
     * Valid transitions: {@code ACTIVE → CLOSED}, {@code ACTIVE → SUSPENDED},
     * {@code CLOSED → ACTIVE}, {@code SUSPENDED → ACTIVE}.
     *
     * @param companyId the target company's id; must not be null
     * @param newStatus the status to transition to; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException    if the requested transition is not permitted
     */
    Company changeStatus(UUID companyId, CompanyStatus newStatus);

    /**
     * Loads a company by id. Closed companies are only visible when {@code canViewClosed} is {@code true};
     * passing {@code false} for a closed company throws an unauthorized exception.
     *
     * @param companyId     the company's id; must not be null
     * @param canViewClosed whether the caller may see closed companies
     * @return the company with the given id
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    Company getCompany(UUID companyId, boolean canViewClosed);

    /**
     * Looks up a company by id without throwing when absent.
     *
     * @param companyId the company's id; may be null
     * @return an {@link Optional} containing the company if found, or empty when the id is null
     */
    Optional<Company> findCompany(UUID companyId);
}
