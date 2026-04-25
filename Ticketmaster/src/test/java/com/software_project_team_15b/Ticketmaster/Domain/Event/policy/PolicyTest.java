package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventTestFixtures;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompPurchasePolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyTest {

    private static final ICompPurchasePolicy NOOP_COMPANY = req -> {};
    private static final ICompDiscountPolicy NOOP_DISCOUNT = (sub, req) -> sub;

    @Test
    void max_tickets_policy_rejects_above_cap() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(4);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2000, 1, 1), 5, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null, NOOP_COMPANY))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void max_tickets_policy_allows_at_cap() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(4);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2000, 1, 1), 4, List.of(), null);
        policy.validate(req, null, NOOP_COMPANY);
    }

    @Test
    void age_restriction_rejects_minor() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birth = LocalDate.now().minusYears(10);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birth, 1, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null, NOOP_COMPANY))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void age_restriction_allows_adult() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birth = LocalDate.now().minusYears(25);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birth, 1, List.of(), null);
        policy.validate(req, null, NOOP_COMPANY);
    }

    @Test
    void age_restriction_requires_birth_date() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null, NOOP_COMPANY))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void no_lonely_seat_policy_rejects_lonely_gap() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "A", Money.of("10.00", "USD"));
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        area.addSeat(new Seat(s1, "A", "1"));
        area.addSeat(new Seat(s2, "A", "2"));
        area.addSeat(new Seat(s3, "A", "3"));
        Event event = EventTestFixtures.published(area);

        event.holdSeats(area.areaId(), List.of(s1), UUID.randomUUID());
        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();
        PurchaseRequest req = new PurchaseRequest(event.eventId(), area.areaId(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(s3), null);
        assertThatThrownBy(() -> policy.validate(req, event, NOOP_COMPANY))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void no_lonely_seat_policy_allows_clean_layout() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "A", Money.of("10.00", "USD"));
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        area.addSeat(new Seat(s1, "A", "1"));
        area.addSeat(new Seat(s2, "A", "2"));
        area.addSeat(new Seat(s3, "A", "3"));
        Event event = EventTestFixtures.published(area);

        NoLonelySeatPolicy policy = new NoLonelySeatPolicy();
        PurchaseRequest req = new PurchaseRequest(event.eventId(), area.areaId(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 2, List.of(s1, s2), null);
        policy.validate(req, event, NOOP_COMPANY);
    }

    @Test
    void early_bird_discount_applies_when_active() {
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                Instant.now().plus(Duration.ofDays(1)));
        Money subtotal = Money.of("100.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req, NOOP_DISCOUNT);
        assertThat(result).isEqualTo(Money.of("80.00", "USD"));
    }

    @Test
    void early_bird_discount_does_not_apply_after_window() {
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                Instant.now().minus(Duration.ofDays(1)));
        Money subtotal = Money.of("100.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req, NOOP_DISCOUNT);
        assertThat(result).isEqualTo(Money.of("100.00", "USD"));
    }

    @Test
    void coupon_discount_applies_when_code_matches() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("PROMO10", BigDecimal.valueOf(10));
        Money subtotal = Money.of("200.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), "promo10");
        Money result = policy.apply(subtotal, req, NOOP_DISCOUNT);
        assertThat(result).isEqualTo(Money.of("180.00", "USD"));
    }

    @Test
    void coupon_discount_skips_when_code_missing() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("PROMO10", BigDecimal.valueOf(10));
        Money subtotal = Money.of("200.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req, NOOP_DISCOUNT);
        assertThat(result).isEqualTo(Money.of("200.00", "USD"));
    }

    @Test
    void delegating_purchase_policy_delegates_to_company() {
        boolean[] called = {false};
        ICompPurchasePolicy company = req -> called[0] = true;
        DelegatingEventPurchasePolicy policy = new DelegatingEventPurchasePolicy();
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        policy.validate(req, null, company);
        assertThat(called[0]).isTrue();
    }

    @Test
    void policy_json_round_trip() {
        MaxTicketsPerOrderPolicy original = new MaxTicketsPerOrderPolicy(6);
        var converter = new PolicyJsonConverter.PurchasePolicyConverter();
        String json = converter.convertToDatabaseColumn(original);
        IEventPurchasePolicy restored = converter.convertToEntityAttribute(json);
        assertThat(restored).isInstanceOf(MaxTicketsPerOrderPolicy.class);
        assertThat(((MaxTicketsPerOrderPolicy) restored).max()).isEqualTo(6);
    }
}
