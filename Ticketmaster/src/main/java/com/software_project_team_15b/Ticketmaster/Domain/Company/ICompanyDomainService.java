package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

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
}
