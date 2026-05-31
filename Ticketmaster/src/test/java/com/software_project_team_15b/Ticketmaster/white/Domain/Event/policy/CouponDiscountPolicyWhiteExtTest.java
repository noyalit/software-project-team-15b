package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CouponDiscountPolicyWhiteExtTest {

    private static PurchaseRequest req(String coupon) {
        return new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, List.of(), coupon);
    }

    @Test
    void GivenBlankCode_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new CouponDiscountPolicy("   ", new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void GivenNullCode_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new CouponDiscountPolicy(null, new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenInvalidPercent_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new CouponDiscountPolicy("CODE", new BigDecimal("150")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CouponDiscountPolicy("CODE", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenNullPercent_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new CouponDiscountPolicy("CODE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenNullRequestOnContext_WhenDiscount_ThenReturnsZero() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("CODE", new BigDecimal("20"));
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(null);

        Money result = policy.discount(Money.of("100.00", "USD"), ctx);

        assertThat(result).isEqualTo(Money.zero("USD"));
    }

    @Test
    void GivenMatchingCodeWithDifferentCase_WhenDiscount_ThenAppliesPercent() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("25"));
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(req("summer20"));

        Money result = policy.discount(Money.of("100.00", "USD"), ctx);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void GivenExpiresAtSetButContextNowIsNull_WhenDiscount_ThenAppliesPercent() {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.DAYS);
        CouponDiscountPolicy policy = new CouponDiscountPolicy("CODE", new BigDecimal("10"), expiresAt);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(req("CODE"));
        when(ctx.now()).thenReturn(null);

        Money result = policy.discount(Money.of("100.00", "USD"), ctx);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void GivenNoExpiry_WhenDiscount_ThenAppliesPercent() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("CODE", new BigDecimal("10"));
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(req("CODE"));

        Money result = policy.discount(Money.of("50.00", "USD"), ctx);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void GivenAccessors_WhenCalled_ThenExposeConstructorValues() {
        Instant exp = Instant.now().plusSeconds(60);
        CouponDiscountPolicy policy = new CouponDiscountPolicy("X", new BigDecimal("33"), exp);
        assertThat(policy.code()).isEqualTo("X");
        assertThat(policy.percentage()).isEqualByComparingTo(new BigDecimal("33"));
        assertThat(policy.expiresAt()).isEqualTo(exp);
    }
}
