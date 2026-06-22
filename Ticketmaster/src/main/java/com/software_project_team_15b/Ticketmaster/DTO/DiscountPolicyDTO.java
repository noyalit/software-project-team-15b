package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transport-layer representation of an event discount policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java
 * class names): one of {@code "COUPON"}, {@code "EARLY_BIRD"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "COUPON", "code": "SUMMER25", "percentage": 25, "expiresAt": "2026-08-31T23:59:59Z" }
 *   { "type": "EARLY_BIRD", "percentage": 10, "until": "2026-06-01T00:00:00Z" }
 * </pre>

 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DiscountPolicyDTO.Coupon.class, name = "COUPON"),
        @JsonSubTypes.Type(value = DiscountPolicyDTO.EarlyBird.class, name = "EARLY_BIRD")
})
public sealed interface DiscountPolicyDTO {

    @JsonProperty("type")
    String type();

    IEventDiscountPolicy toDomain();

    static DiscountPolicyDTO fromDomain(IEventDiscountPolicy policy) {
        if (policy instanceof CouponDiscountPolicy c) {
            return new Coupon(c.code(), c.percentage(), c.expiresAt());
        }
        if (policy instanceof EarlyBirdDiscountPolicy e) {
            return new EarlyBird(e.percentage(), e.until());
        }
        throw new IllegalArgumentException(
                "Unsupported discount policy type for wire format: " + policy.getClass().getName());
    }

    record Coupon(String code, BigDecimal percentage, Instant expiresAt) implements DiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "COUPON";
        }

        @Override
        public IEventDiscountPolicy toDomain() {
            return new CouponDiscountPolicy(code, percentage, expiresAt);
        }
    }

    record EarlyBird(BigDecimal percentage, Instant until) implements DiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "EARLY_BIRD";
        }

        @Override
        public IEventDiscountPolicy toDomain() {
            return new EarlyBirdDiscountPolicy(percentage, until);
        }
    }
}
