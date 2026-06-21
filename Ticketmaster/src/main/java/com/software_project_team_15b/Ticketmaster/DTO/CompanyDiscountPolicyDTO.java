package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Transport-layer representation of a company discount policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java class
 * names): one of {@code "SIMPLE"}, {@code "COUPON"}, {@code "CONDITIONAL"} (leaves),
 * or {@code "SUM"}, {@code "MAX"} (composite roots that carry a {@code children} list).
 *
 * <p>Examples:
 * <pre>
 *   { "type": "SIMPLE", "percent": 10 }
 *   { "type": "COUPON", "code": "SUMMER25", "percentage": 25, "expiresAt": "2026-08-31T23:59:59Z" }
 *   { "type": "CONDITIONAL", "percent": 20, "condition": { "type": "MAX_TICKETS", "max": 4 } }
 *   { "type": "MAX", "children": [ { "type": "SIMPLE", "percent": 10 }, { "type": "SIMPLE", "percent": 20 } ] }
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
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Conditional.class, name = "CONDITIONAL"),
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Sum.class, name = "SUM"),
        @JsonSubTypes.Type(value = CompanyDiscountPolicyDTO.Max.class, name = "MAX")
})
public sealed interface CompanyDiscountPolicyDTO {

    @JsonProperty("type")
    String type();

    ICompanyDiscountPolicy toDomain();

    static CompanyDiscountPolicyDTO fromDomain(ICompanyDiscountPolicy policy) {
        if (policy instanceof SumDiscountPolicy s) {
            return new Sum(s.children().stream().map(CompanyDiscountPolicyDTO::fromChild).toList());
        }
        if (policy instanceof MaxDiscountPolicy m) {
            return new Max(m.children().stream().map(CompanyDiscountPolicyDTO::fromChild).toList());
        }
        if (policy instanceof CouponDiscountPolicy c) {
            return new Coupon(c.code(), c.percentage(), c.expiresAt());
        }
        if (policy instanceof ConditionalDiscountPolicy c) {
            return new Conditional(c.percent(), CompanyDiscountConditionDTO.fromDomain(c.condition()));
        }
        if (policy instanceof SimpleDiscountPolicy s) {
            return new Simple(s.percent());
        }
        if (policy instanceof EarlyBirdDiscountPolicy e) {
            return new EarlyBird(e.percentage(), e.until());
        }
        if (policy instanceof SumDiscountPolicy sum) {
            return new Sum(mapChildren(sum.children()));
        }
        if (policy instanceof MaxDiscountPolicy max) {
            return new Max(mapChildren(max.children()));
        }
        throw new IllegalArgumentException(
                "Unsupported company discount policy type for wire format: " + policy.getClass().getName());
    }

    /**
     * Maps a composite child back to its DTO. Children of a persisted company discount
     * root are themselves company discount policies (leaves or nested composites), so
     * the cast holds; a non-company child surfaces as the same "unsupported" error.
     */
    private static CompanyDiscountPolicyDTO fromChild(IDiscountPolicy child) {
        if (child instanceof ICompanyDiscountPolicy c) {
            return fromDomain(c);
        }
        throw new IllegalArgumentException(
                "Unsupported company discount policy type for wire format: " + child.getClass().getName());
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

    record Sum(List<CompanyDiscountPolicyDTO> children) implements CompanyDiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "SUM";
        }

        @Override
        public ICompanyDiscountPolicy toDomain() {
            return new SumDiscountPolicy(children.stream()
                    .map(CompanyDiscountPolicyDTO::toDomain)
                    .map(d -> (IDiscountPolicy) d)
                    .toList());
        }
    }

    record Max(List<CompanyDiscountPolicyDTO> children) implements CompanyDiscountPolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MAX";
        }

        @Override
        public ICompanyDiscountPolicy toDomain() {
            return new MaxDiscountPolicy(children.stream()
                    .map(CompanyDiscountPolicyDTO::toDomain)
                    .map(d -> (IDiscountPolicy) d)
                    .toList());
        }
    }
}
