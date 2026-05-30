package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Coupon-gated percentage discount. Applies the configured percent of the subtotal as a
 * discount iff the buyer presents the matching code (case-insensitive) and, if
 * {@code expiresAt} is set, the current instant has not passed it.
 *
 * <p>Attachable at both Event and Company levels.
 */
public class CouponDiscountPolicy implements IEventDiscountPolicy, ICompanyDiscountPolicy {

    @JsonProperty("code")
    private final String code;

    @JsonProperty("percentage")
    private final BigDecimal percentage;

    @JsonProperty("expiresAt")
    private final Instant expiresAt;

    @JsonCreator
    public CouponDiscountPolicy(
            @JsonProperty("code") String code,
            @JsonProperty("percentage") BigDecimal percentage,
            @JsonProperty("expiresAt") Instant expiresAt) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("coupon code must not be blank");
        }
        IDiscountPolicy.requireValidPercent(percentage);
        this.code = code;
        this.percentage = percentage;
        this.expiresAt = expiresAt;
    }

    public CouponDiscountPolicy(String code, BigDecimal percentage) {
        this(code, percentage, null);
    }

    public String code() { return code; }
    public BigDecimal percentage() { return percentage; }
    public Instant expiresAt() { return expiresAt; }

    @Override
    public Money discount(Money subtotal, PolicyContext ctx) {
        if (ctx == null || ctx.request() == null || ctx.request().couponCode() == null) {
            return Money.zero(subtotal.currency());
        }
        if (!code.equalsIgnoreCase(ctx.request().couponCode())) {
            return Money.zero(subtotal.currency());
        }
        if (expiresAt != null && ctx.now() != null && ctx.now().isAfter(expiresAt)) {
            return Money.zero(subtotal.currency());
        }
        return subtotal.percent(percentage);
    }
}
