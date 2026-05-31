package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class EarlyBirdDiscountPolicyWhiteExtTest {

    @Test
    void GivenNullUntil_WhenConstruct_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> new EarlyBirdDiscountPolicy(new BigDecimal("10"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenInvalidPercent_WhenConstruct_ThenThrowsIllegalArgument() {
        Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> new EarlyBirdDiscountPolicy(new BigDecimal("-1"), until))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EarlyBirdDiscountPolicy(new BigDecimal("101"), until))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenContextWithNullNow_WhenDiscount_ThenFallsBackToInstantNow() {
        Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("20"), until);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(null);

        Money result = policy.discount(Money.of("100.00", "USD"), ctx);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void GivenNowAtUntilBoundary_WhenDiscount_ThenReturnsZero() {
        Instant until = Instant.now();
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("20"), until);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(until);

        Money result = policy.discount(Money.of("100.00", "USD"), ctx);

        assertThat(result).isEqualTo(Money.zero("USD"));
    }

    @Test
    void GivenAccessors_WhenCalled_ThenExposeConstructorValues() {
        Instant until = Instant.now().plus(2, ChronoUnit.HOURS);
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(new BigDecimal("15"), until);

        assertThat(policy.percentage()).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(policy.until()).isEqualTo(until);
    }
}
