package com.software_project_team_15b.Ticketmaster.Domain.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import java.math.BigDecimal;

/**
 * Component role in the Composite pattern for discount trees.
 *
 * <p>Implementations return the discount amount (not the post-discount price) for the
 * given subtotal. Returning {@code Money.zero} means "no discount". The amount is
 * additive and may be combined by {@code SumDiscountPolicy} or compared by
 * {@code MaxDiscountPolicy}.
 *
 * <p>{@link #apply(Money, PurchaseRequest)} is preserved for the existing event/company
 * code paths that call it directly; it returns the post-discount price computed via
 * {@code subtotal.subtract(discount(...))}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IDiscountPolicy {

    Money discount(Money subtotal, PolicyContext ctx);

    default Money apply(Money subtotal, PurchaseRequest request) {
        Money d = discount(subtotal, new PolicyContext(request, null, null, java.time.Instant.now()));
        if (d == null || d.amount().signum() <= 0) return subtotal;
        Money capped = d.amount().compareTo(subtotal.amount()) > 0
                ? subtotal
                : d;
        return subtotal.subtract(capped);
    }

    /** Helper: clamp a discount candidate to [0, subtotal]. */
    static Money clamp(Money candidate, Money subtotal) {
        if (candidate == null || candidate.amount().signum() <= 0) {
            return Money.zero(subtotal.currency());
        }
        if (candidate.amount().compareTo(subtotal.amount()) > 0) {
            return subtotal;
        }
        return candidate;
    }

    /** Helper: validate a percentage value is in [0, 100]. */
    static void requireValidPercent(BigDecimal percent) {
        if (percent == null) throw new IllegalArgumentException("percent must not be null");
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percent must be in [0, 100]");
        }
    }
}
