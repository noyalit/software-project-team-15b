package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompDiscountPolicy;
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
    public Money apply(Money subtotal, PurchaseRequest request, ICompDiscountPolicy companyPolicy) {
        Money afterCompany = companyPolicy != null ? companyPolicy.apply(subtotal, request) : subtotal;
        if (code.equalsIgnoreCase(request.couponCode())) {
            return afterCompany.subtract(afterCompany.percent(percentage));
        }
        return afterCompany;
    }
}
