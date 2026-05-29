package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventCheapestPriceTest {

    @Test
    void GivenNoDiscounts_WhenCheapestPriceFor_ThenReturnsSubtotal() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "20.00");
        Event event = newPublished(List.of(), area);

        Money total = event.cheapestPriceFor(area.areaId(), 4, null);

        assertThat(total).isEqualTo(EventTestFixtures.usd("80.00"));
    }

    @Test
    void GivenMultipleDiscountPolicies_WhenCheapestPriceFor_ThenReturnsLowestTotal() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = newPublished(List.of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(10),
                        Instant.now().plus(Duration.ofDays(1))),
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(25),
                        Instant.now().plus(Duration.ofDays(1)))
        ), area);

        Money total = event.cheapestPriceFor(area.areaId(), 2, null);

        // subtotal is 200, best discount is 25% -> 150
        assertThat(total).isEqualTo(EventTestFixtures.usd("150.00"));
    }

    @Test
    void GivenTwoActiveDiscounts_WhenCheapestPriceFor_ThenDoesNotCombineThem() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        // 20% + 30% would be 50 if combined; cheapest single is 30% -> 70
        Event event = newPublished(List.of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                        Instant.now().plus(Duration.ofDays(1))),
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(30),
                        Instant.now().plus(Duration.ofDays(1)))
        ), area);

        Money total = event.cheapestPriceFor(area.areaId(), 1, null);

        assertThat(total).isEqualTo(EventTestFixtures.usd("70.00"));
    }

    @Test
    void GivenExpiredAndActiveDiscounts_WhenCheapestPriceFor_ThenIgnoresExpiredOne() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = newPublished(List.of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(50),
                        Instant.now().minus(Duration.ofDays(1))), // expired -> returns subtotal
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(10),
                        Instant.now().plus(Duration.ofDays(1)))
        ), area);

        Money total = event.cheapestPriceFor(area.areaId(), 1, null);

        assertThat(total).isEqualTo(EventTestFixtures.usd("90.00"));
    }

    @Test
    void GivenCouponAndEarlyBird_WhenCheapestPriceFor_ThenAppliesCouponOnlyWhenCodeMatches() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        Event event = newPublished(List.of(
                new CouponDiscountPolicy("PROMO", BigDecimal.valueOf(40)),
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(15),
                        Instant.now().plus(Duration.ofDays(1)))
        ), area);

        // No coupon code in request -> coupon discount is a no-op (returns subtotal),
        // so cheapest is the 15% early bird = 85.
        PurchaseRequest noCoupon = new PurchaseRequest(event.eventId(), area.areaId(),
                UUID.randomUUID(), LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money totalNoCoupon = event.cheapestPriceFor(area.areaId(), 1, noCoupon);
        assertThat(totalNoCoupon).isEqualTo(EventTestFixtures.usd("85.00"));

        // With coupon -> 40% beats 15%, total = 60.
        // Note: the policy stores "PROMO" but the request sends "promo" — coupon
        // matching is intentionally case-insensitive (CouponDiscountPolicy uses
        // String.equalsIgnoreCase). This assertion pins that contract.
        PurchaseRequest withCoupon = new PurchaseRequest(event.eventId(), area.areaId(),
                UUID.randomUUID(), LocalDate.of(1990, 1, 1), 1, List.of(), "promo");
        Money totalWithCoupon = event.cheapestPriceFor(area.areaId(), 1, withCoupon);
        assertThat(totalWithCoupon).isEqualTo(EventTestFixtures.usd("60.00"));
    }

    @Test
    void GivenDiscountThatExceedsSubtotal_WhenCheapestPriceFor_ThenIsClampedToZero() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "100.00");
        IEventDiscountPolicy oversized = (subtotal, ctx) ->
                subtotal.add(Money.of("50.00", subtotal.currency()));
        Event event = newPublished(List.of(oversized), area);

        Money total = event.cheapestPriceFor(area.areaId(), 1, null);

        assertThat(total).isEqualTo(EventTestFixtures.usd("0.00"));
    }

    private static Event newPublished(List<IEventDiscountPolicy> discounts, SeatingEventArea area) {
        Event event = new Event(
                UUID.randomUUID(), UUID.randomUUID(), "Test Event", "Artist",
                Category.CONCERT, Instant.now().plusSeconds(86400), "Venue",
                List.of(),
                discounts
        );
        event.addArea(area);
        event.publish();
        return event;
    }
}
