package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transport-layer representation of a company discount policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java class
 * names): one of {@code "SIMPLE"}, {@code "COUPON"}, {@code "CONDITIONAL"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "SIMPLE", "percent": 10 }
 *   { "type": "COUPON", "code": "SUMMER25", "percentage": 25, "expiresAt": "2026-08-31T23:59:59Z" }
 *   { "type": "CONDITIONAL", "percent": 20, "condition": { "type": "MAX_TICKETS", "max": 4 } }
 * </pre>
 *
 * <p>The discriminator uses {@link JsonTypeInfo.As#EXISTING_PROPERTY} backed by a real
 * {@code type} accessor so it is always serialized — including when the value travels
 * through a generic wrapper such as {@code ApiResponse<List<...>>}, where Jackson would
 * otherwise omit a synthesized type id.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Simple.class, name = "SIMPLE"),
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Coupon.class, name = "COUPON"),
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Conditional.class, name = "CONDITIONAL")
})
public sealed interface CompanyDiscountPolicyDTO {

    @JsonProperty("type")
    String type();

    ICompanyDiscountPolicy toDomain();

    static CompanyDiscountPolicyDTO fromDomain(ICompanyDiscountPolicy policy) {
        if (policy instanceof CouponDiscountPolicy c) {
            return new Coupon(c.code(), c.percentage(), c.expiresAt());
        }
        if (policy instanceof ConditionalDiscountPolicy c) {
            return new Conditional(c.percent(), CompanyDiscountConditionDTO.fromDomain(c.condition()));
        }
        if (policy instanceof SimpleDiscountPolicy s) {
            return new Simple(s.percent());
        }
        throw new IllegalArgumentException(
                "Unsupported company discount policy type for wire format: " + policy.getClass().getName());
    }

    record Simple(BigDecimal percent) implements CompanyDiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "SIMPLE";
        }

        @Override
        public ICompanyDiscountPolicy toDomain() {
            return new SimpleDiscountPolicy(percent);
        }
    }

    record Coupon(String code, BigDecimal percentage, Instant expiresAt) implements CompanyDiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "COUPON";
        }

        @Override
        public ICompanyDiscountPolicy toDomain() {
            return new CouponDiscountPolicy(code, percentage, expiresAt);
        }
    }

    record Conditional(BigDecimal percent, CompanyDiscountConditionDTO condition) implements CompanyDiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "CONDITIONAL";
        }

        @Override
        public ICompanyDiscountPolicy toDomain() {
            return new ConditionalDiscountPolicy(percent, condition.toDomain());
        }
    }
}
