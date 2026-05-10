package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import java.math.BigDecimal;

public class CouponDiscountPolicy implements IEventDiscountPolicy {

    @JsonProperty("code")
    private final String code;

    @JsonProperty("percentage")
    private final BigDecimal percentage;

    @JsonCreator
    public CouponDiscountPolicy(
            @JsonProperty("code") String code,
            @JsonProperty("percentage") BigDecimal percentage) {
        this.code = code;
        this.percentage = percentage;
    }

    public String code() { return code; }
    public BigDecimal percentage() { return percentage; }

    @Override
    public Money apply(Money subtotal, PurchaseRequest request) {
        if (request.couponCode() == null) return subtotal;
        if (code.equalsIgnoreCase(request.couponCode())) {
            return subtotal.subtract(subtotal.percent(percentage));
        }
        return subtotal;
    }
}
