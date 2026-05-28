package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

import java.util.Set;
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
     * Aggregate discount AMOUNT (not post-discount price) produced by the company's
     * discount policy tree for the given subtotal. Returns zero in the subtotal's
     * currency when no policy applies or the company cannot be found.
     */
    Money discountAmountFor(UUID companyId, Money subtotal, PurchaseRequest request);

    /**
     * @return the company's configured strategy for combining event-level and
     *         company-level discounts, defaulting to
     *         {@link com.software_project_team_15b.Ticketmaster.Domain.Company.DiscountCombineStrategy#SUM SUM}
     *         when the company is unknown.
     */
    DiscountCombineStrategy discountCombineStrategyFor(UUID companyId);

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
     * Returns the set of owner ids for the company. Only an existing owner
     * of the company may query this information.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @return an unmodifiable set of owner ids
     * @throws IllegalArgumentException           if {@code companyId} is null or blank
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public Set<UUID> getOwnerIds(String token, UUID companyId);

    /**
     * Returns {@code true} if {@code userId} is the founder or an owner of the given company.
     *
     * @param companyId the company to check; must not be null
     * @param userId    the user to check; must not be null
     * @return {@code true} if the user is the founder or is in the owner set
     * @throws IllegalArgumentException if {@code companyId} or {@code userId} is null
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public boolean isCompanyFounderOrOwner(UUID companyId, UUID userId);

    /**
     * Returns {@code true} if {@code userId} is registered as a manager for {@code eventId}
     * in the company that owns the event.
     *
     * @param eventId the event to check; must not be null
     * @param userId  the user to check; must not be null
     * @return {@code true} if the user is an event manager for that event
     * @throws IllegalArgumentException if {@code eventId} or {@code userId} is null
     * @throws CompanyNotFoundException if the owning company cannot be found
     */
    public boolean isEventManager(UUID eventId, UUID userId);
}
