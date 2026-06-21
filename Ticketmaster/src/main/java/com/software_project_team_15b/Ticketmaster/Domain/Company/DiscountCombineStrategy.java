package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Strategy used by {@code EventDomainServiceImpl.getPrice} to combine the discount
 * computed by the event-level policy tree with the discount computed by the
 * company-level policy tree. Configurable per company.
 */
public enum DiscountCombineStrategy {

    /** Stack additively: total = clamp(event + company, subtotal). */
    SUM {
        @Override
        public Money combine(Money eventDiscount, Money companyDiscount, Money subtotal) {
            Money sum = eventDiscount.add(companyDiscount);
            return sum.amount().compareTo(subtotal.amount()) > 0 ? subtotal : sum;
        }
    },

    /**
     * Stack multiplicatively (כפל הנחות) — the company discount applies to the running price
     * the event discount already left, mirroring {@code SumDiscountPolicy}'s cascade within a
     * single tree. For amounts {@code e} and {@code c} taken on {@code subtotal S}:
     * {@code total = S - (S - e)(S - c) / S = e + c - e·c/S}. Order-independent, and two finite
     * discounts can never exceed the subtotal. e.g. 5% then 10%+10% on 200 → 46.10 off (153.90 final).
     */
    CASCADE {
        @Override
        public Money combine(Money eventDiscount, Money companyDiscount, Money subtotal) {
            if (subtotal.amount().signum() == 0) return Money.zero(subtotal.currency());
            BigDecimal afterEvent = subtotal.subtract(IDiscountPolicy.clamp(eventDiscount, subtotal)).amount();
            BigDecimal afterCompany = subtotal.subtract(IDiscountPolicy.clamp(companyDiscount, subtotal)).amount();
            BigDecimal running = afterEvent.multiply(afterCompany)
                    .divide(subtotal.amount(), 2, RoundingMode.HALF_UP);
            return subtotal.subtract(new Money(running, subtotal.currency()));
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
