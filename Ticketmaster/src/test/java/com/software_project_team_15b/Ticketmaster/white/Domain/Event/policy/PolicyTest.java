package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.*;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyTest {

    @Test
    void max_tickets_policy_rejects_above_cap() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(4);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2000, 1, 1), 5, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void max_tickets_policy_allows_at_cap() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(4);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2000, 1, 1), 4, List.of(), null);
        policy.validate(req, null);
    }

    @Test
    void age_restriction_rejects_minor() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birth = LocalDate.now().minusYears(10);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birth, 1, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void age_restriction_allows_adult() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birth = LocalDate.now().minusYears(25);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birth, 1, List.of(), null);
        policy.validate(req, null);
    }

    @Test
    void age_restriction_requires_birth_date() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, List.of(), null);
        assertThatThrownBy(() -> policy.validate(req, null))
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
        assertThatThrownBy(() -> policy.validate(req, event))
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
        policy.validate(req, event);
    }

    @Test
    void early_bird_discount_applies_when_active() {
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                Instant.now().plus(Duration.ofDays(1)));
        Money subtotal = Money.of("100.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req);
        assertThat(result).isEqualTo(Money.of("80.00", "USD"));
    }

    @Test
    void early_bird_discount_does_not_apply_after_window() {
        EarlyBirdDiscountPolicy policy = new EarlyBirdDiscountPolicy(BigDecimal.valueOf(20),
                Instant.now().minus(Duration.ofDays(1)));
        Money subtotal = Money.of("100.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req);
        assertThat(result).isEqualTo(Money.of("100.00", "USD"));
    }

    @Test
    void coupon_discount_applies_when_code_matches() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("PROMO10", BigDecimal.valueOf(10));
        Money subtotal = Money.of("200.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), "promo10");
        Money result = policy.apply(subtotal, req);
        assertThat(result).isEqualTo(Money.of("180.00", "USD"));
    }

    @Test
    void coupon_discount_skips_when_code_missing() {
        CouponDiscountPolicy policy = new CouponDiscountPolicy("PROMO10", BigDecimal.valueOf(10));
        Money subtotal = Money.of("200.00", "USD");
        PurchaseRequest req = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
        Money result = policy.apply(subtotal, req);
        assertThat(result).isEqualTo(Money.of("200.00", "USD"));
    }

    @Test
    void policy_json_round_trip() {
        List<IEventPurchasePolicy> originals = List.of(
                new MaxTicketsPerOrderPolicy(6),
                new AgeRestrictionPolicy(18)
        );
        var converter = new PolicyJsonConverter.PurchasePolicyListConverter();
        String json = converter.convertToDatabaseColumn(originals);
        List<IEventPurchasePolicy> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0)).isInstanceOf(MaxTicketsPerOrderPolicy.class);
        assertThat(((MaxTicketsPerOrderPolicy) restored.get(0)).max()).isEqualTo(6);
        assertThat(restored.get(1)).isInstanceOf(AgeRestrictionPolicy.class);
    }

    @Test
    void discount_policy_list_json_round_trip() {
        List<IEventDiscountPolicy> originals = List.of(
                new EarlyBirdDiscountPolicy(BigDecimal.valueOf(15),
                        Instant.now().plus(Duration.ofDays(1))),
                new CouponDiscountPolicy("PROMO", BigDecimal.valueOf(20))
        );
        var converter = new PolicyJsonConverter.DiscountPolicyListConverter();
        String json = converter.convertToDatabaseColumn(originals);
        List<IEventDiscountPolicy> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0)).isInstanceOf(EarlyBirdDiscountPolicy.class);
        assertThat(restored.get(1)).isInstanceOf(CouponDiscountPolicy.class);
    }

    @Test
    void purchase_policy_converter_handles_null_and_blank_as_empty_list() {
        var converter = new PolicyJsonConverter.PurchasePolicyListConverter();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }
}
