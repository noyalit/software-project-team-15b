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
     * Returns all companies in which the given member is listed as an owner.
     *
     * @param ownerId the owner's id; must not be null
     * @return a non-null, possibly empty list of matching companies
     */
    List<Company> findCompaniesByOwner(UUID ownerId);

    /**
     * Replaces the company's purchase policy. Only an owner or founder of the company
     * may perform this action.
     *
     * @param companyId the target company's id; must not be null
     * @param callerId  the id of the caller; must not be null
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException
     *                                            if the caller is not an owner or founder
     */
    Company updatePurchasePolicy(UUID companyId, UUID callerId, ICompanyPurchasePolicy policy);

    /**
     * Replaces the company's discount policy. Only an owner or founder of the company
     * may perform this action.
     *
     * @param companyId the target company's id; must not be null
     * @param callerId  the id of the caller; must not be null
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException
     *                                            if the caller is not an owner or founder
     */
    Company updateDiscountPolicy(UUID companyId, UUID callerId, ICompanyDiscountPolicy policy);

    /**
     * Transitions the company to the given status. Only the founder or a system admin
     * may perform this action. When transitioning to {@link CompanyStatus#CLOSED}, all
     * events belonging to the company are cancelled.
     *
     * @param companyId     the target company's id; must not be null
     * @param callerId      the id of the caller, or {@code null} if caller is not a member
     * @param isSystemAdmin {@code true} if the caller holds system-admin privileges
     * @param newStatus     the status to transition to; must not be null
     * @return the updated, persisted company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException
     *                                            if the caller is neither the founder nor a system admin
     */
    Company changeStatus(UUID companyId, UUID callerId, boolean isSystemAdmin, CompanyStatus newStatus);

    /**
     * Loads a company by id, failing if it does not exist.
     *
     * @param companyId the company's id; must not be null
     * @return the company with the given id
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    Company getCompany(UUID companyId);

    /**
     * Looks up a company by id without throwing when absent.
     *
     * @param companyId the company's id; may be null
     * @return an {@link Optional} containing the company if found, or empty when the id is null
     */
    Optional<Company> findCompany(UUID companyId);
}
