package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EarlyBirdDiscountPolicyWhiteTest {

    @Test
    void GivenNowIsBeforeUntil_WhenDiscount_ThenReturnDiscountedMoney() {
        Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("10.00"), until);
        Money subtotal = Money.of("100.00", "USD");

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("10.00", "USD"), discount);
    }

    @Test
    void GivenNowIsAfterUntil_WhenDiscount_ThenReturnZero() {
        Instant until = Instant.now().minus(1, ChronoUnit.DAYS);
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("10.00"), until);
        Money subtotal = Money.of("100.00", "USD");

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenContextIsNull_WhenDiscount_ThenFallbackToInstantNow() {
        Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("20.00"), until);
        Money subtotal = Money.of("50.00", "EUR");

        Money discount = policy.discount(subtotal, null);

        assertEquals(Money.of("10.00", "EUR"), discount);
    }
}
