package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.IDiscountCondition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConditionalDiscountPolicyWhiteTest {

    @Test
    void GivenConditionTrue_WhenDiscount_ThenReturnDiscountedMoney() {
        IDiscountCondition condition = mock(IDiscountCondition.class);
        PolicyContext ctx = mock(PolicyContext.class);
        when(condition.test(ctx)).thenReturn(true);

        ConditionalDiscountPolicy policy = new ConditionalDiscountPolicy(new BigDecimal("10.00"), condition);
        Money subtotal = Money.of("100.00", "USD");

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("10.00", "USD"), discount);
    }

    @Test
    void GivenConditionFalse_WhenDiscount_ThenReturnZero() {
        IDiscountCondition condition = mock(IDiscountCondition.class);
        PolicyContext ctx = mock(PolicyContext.class);
        when(condition.test(ctx)).thenReturn(false);

        ConditionalDiscountPolicy policy = new ConditionalDiscountPolicy(new BigDecimal("10.00"), condition);
        Money subtotal = Money.of("100.00", "USD");

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenNullCondition_WhenCreatePolicy_ThenThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ConditionalDiscountPolicy(new BigDecimal("10.00"), null));
    }
}
