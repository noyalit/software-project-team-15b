package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaxDiscountPolicyWhiteTest {

    @Test
    void GivenNoChildren_WhenDiscount_ThenReturnZero() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy(Collections.emptyList());
        Money subtotal = Money.of("100.00", "USD");

        Money discount = policy.discount(subtotal, null);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenMultipleChildren_WhenDiscount_ThenReturnLargestDiscount() {
        IDiscountPolicy child1 = mock(IDiscountPolicy.class);
        IDiscountPolicy child2 = mock(IDiscountPolicy.class);
        IDiscountPolicy child3 = mock(IDiscountPolicy.class);
        when(child1.discount(any(), any())).thenReturn(Money.of("10.00", "USD"));
        when(child2.discount(any(), any())).thenReturn(Money.of("25.00", "USD"));
        when(child3.discount(any(), any())).thenReturn(Money.of("15.00", "USD"));

        MaxDiscountPolicy policy = new MaxDiscountPolicy(List.of(child1, child2, child3));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("25.00", "USD"), discount);
    }

    @Test
    void GivenChildDiscountExceedsSubtotal_WhenDiscount_ThenReturnClampedToSubtotal() {
        IDiscountPolicy child1 = mock(IDiscountPolicy.class);
        IDiscountPolicy child2 = mock(IDiscountPolicy.class);
        when(child1.discount(any(), any())).thenReturn(Money.of("10.00", "USD"));
        when(child2.discount(any(), any())).thenReturn(Money.of("120.00", "USD"));

        MaxDiscountPolicy policy = new MaxDiscountPolicy(List.of(child1, child2));
        Money subtotal = Money.of("100.00", "USD");
        PolicyContext ctx = mock(PolicyContext.class);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("100.00", "USD"), discount);
    }
}
