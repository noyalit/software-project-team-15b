package main.java.com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompDiscountPolicy;
import java.math.BigDecimal;
import java.time.Instant;

public class EarlyBirdDiscountPolicy implements IEventDiscountPolicy {

    @JsonProperty("percentage")
    private final BigDecimal percentage;

    @JsonProperty("until")
    private final Instant until;

    @JsonCreator
    public EarlyBirdDiscountPolicy(
            @JsonProperty("percentage") BigDecimal percentage,
            @JsonProperty("until") Instant until) {
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be in [0, 100]");
        }
        this.percentage = percentage;
        this.until = until;
    }

    public BigDecimal percentage() { return percentage; }
    public Instant until() { return until; }

    @Override
    public Money apply(Money subtotal, PurchaseRequest request, ICompDiscountPolicy companyPolicy) {
        Money afterCompany = companyPolicy != null ? companyPolicy.apply(subtotal, request) : subtotal;
        if (Instant.now().isBefore(until)) {
            return afterCompany.subtract(afterCompany.percent(percentage));
        }
        return afterCompany;
    }
}
