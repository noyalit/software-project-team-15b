package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SumDiscountPolicyWhiteTest {

    @Test
    void GivenNoChildren_WhenDiscount_ThenReturnZero() {
        SumDiscountPolicy policy = new SumDiscountPolicy(Collections.emptyList());
        Money subtotal = Money.of("100.00", "USD");

        Money discount = policy.discount(subtotal, null);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenMultipleChildren_WhenDiscount_ThenReturnSumOfDiscounts() {
        IDiscountPolicy child1 = mock(IDiscountPolicy.class);
        IDiscountPolicy child2 = mock(IDiscountPolicy.class);
        when(child1.discount(any(), any())).thenReturn(Money.of("10.00", "USD"));
        when(child2.discount(any(), any())).thenReturn(Money.of("15.00", "USD"));

        SumDiscountPolicy policy = new SumDiscountPolicy(List.of(child1, child2));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("25.00", "USD"), discount);
    }

    @Test
    void GivenSumExceedsSubtotal_WhenDiscount_ThenReturnClampedToSubtotal() {
        IDiscountPolicy child1 = mock(IDiscountPolicy.class);
        IDiscountPolicy child2 = mock(IDiscountPolicy.class);
        when(child1.discount(any(), any())).thenReturn(Money.of("60.00", "USD"));
        when(child2.discount(any(), any())).thenReturn(Money.of("50.00", "USD"));

        SumDiscountPolicy policy = new SumDiscountPolicy(List.of(child1, child2));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("100.00", "USD"), discount);
    }

    @Test
    void GivenChildDiscountExceedsSubtotal_WhenDiscount_ThenChildDiscountIsClampedFirst() {
        IDiscountPolicy child1 = mock(IDiscountPolicy.class);
        when(child1.discount(any(), any())).thenReturn(Money.of("120.00", "USD"));

        SumDiscountPolicy policy = new SumDiscountPolicy(List.of(child1));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("100.00", "USD"), discount);
    }
}
