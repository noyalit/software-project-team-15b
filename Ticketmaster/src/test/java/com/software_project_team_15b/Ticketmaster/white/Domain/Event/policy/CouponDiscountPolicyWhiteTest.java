package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponDiscountPolicyWhiteTest {

    @Test
    void GivenValidCouponCode_WhenDiscount_ThenReturnDiscountedMoney() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("20.00"));
        Money subtotal = Money.of("100.00", "USD");

        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, "SUMMER20"
        );
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.of("20.00", "USD"), discount);
    }

    @Test
    void GivenInvalidCouponCode_WhenDiscount_ThenReturnZero() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("20.00"));
        Money subtotal = Money.of("100.00", "USD");

        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, "WINTER10"
        );
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenNoCouponCode_WhenDiscount_ThenReturnZero() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("20.00"));
        Money subtotal = Money.of("100.00", "USD");

        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, null
        );
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenExpiredCoupon_WhenDiscount_ThenReturnZero() {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.DAYS);
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("20.00"), expiresAt);
        Money subtotal = Money.of("100.00", "USD");

        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, "SUMMER20"
        );
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);
        when(ctx.now()).thenReturn(Instant.now());

        Money discount = policy.discount(subtotal, ctx);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenValidCouponCodeButContextNull_WhenDiscount_ThenReturnZero() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("SUMMER20", new BigDecimal("20.00"));
        Money subtotal = Money.of("100.00", "USD");

        Money discount = policy.discount(subtotal, null);

        assertEquals(Money.zero("USD"), discount);
    }

    @Test
    void GivenBlankCode_WhenCreatePolicy_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CouponDiscountPolicy(" ", new BigDecimal("20.00")));
    }
}
