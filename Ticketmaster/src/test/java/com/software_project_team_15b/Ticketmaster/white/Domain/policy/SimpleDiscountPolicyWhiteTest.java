package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SimpleDiscountPolicyWhiteTest {

    @Test
    void GivenValidPercent_WhenDiscount_ThenReturnDiscountedMoney() {
        SimpleDiscountPolicy policy = new SimpleDiscountPolicy(new BigDecimal("15.00"));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("15.00", "USD"), discount);
    }

    @Test
    void GivenNegativePercent_WhenCreatePolicy_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleDiscountPolicy(new BigDecimal("-5.00")));
    }

    @Test
    void GivenOverOneHundredPercent_WhenCreatePolicy_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleDiscountPolicy(new BigDecimal("105.00")));
    }
}
