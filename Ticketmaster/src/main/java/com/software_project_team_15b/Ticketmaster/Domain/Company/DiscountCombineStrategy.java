package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

/**
 * Strategy used by {@code EventDomainServiceImpl.getPrice} to combine the discount
 * computed by the event-level policy tree with the discount computed by the
 * company-level policy tree. Configurable per company.
 */
public enum DiscountCombineStrategy {

    /** Stack: total = clamp(event + company, subtotal). */
    SUM {
        @Override
        public Money combine(Money eventDiscount, Money companyDiscount, Money subtotal) {
            Money sum = eventDiscount.add(companyDiscount);
            return sum.amount().compareTo(subtotal.amount()) > 0 ? subtotal : sum;
        }
    },

    /** No stacking: total = max(event, company). */
    MAX {
        @Override
        public Money combine(Money eventDiscount, Money companyDiscount, Money subtotal) {
            return eventDiscount.amount().compareTo(companyDiscount.amount()) >= 0
                    ? eventDiscount
                    : companyDiscount;
        }
    };

    public abstract Money combine(Money eventDiscount, Money companyDiscount, Money subtotal);
}
