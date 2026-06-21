package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.NoLonelySeatPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;

/**
 * Transport-layer representation of an event purchase policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java
 * class names): one of
 * {@code "MAX_TICKETS_PER_ORDER"}, {@code "AGE_RESTRICTION"},
 * {@code "MIN_TICKETS_PER_ORDER"}, {@code "NO_LONELY_SEAT"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "MAX_TICKETS_PER_ORDER", "max": 4 }
 *   { "type": "AGE_RESTRICTION", "minAge": 18 }
 *   { "type": "MIN_TICKETS_PER_ORDER", "min": 2 }
 *   { "type": "NO_LONELY_SEAT" }
 * </pre>

 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PurchasePolicyDTO.MaxTicketsPerOrder.class, name = "MAX_TICKETS_PER_ORDER"),
        @JsonSubTypes.Type(value = PurchasePolicyDTO.AgeRestriction.class, name = "AGE_RESTRICTION"),
        @JsonSubTypes.Type(value = PurchasePolicyDTO.MinTicketsPerOrder.class, name = "MIN_TICKETS_PER_ORDER"),
        @JsonSubTypes.Type(value = PurchasePolicyDTO.NoLonelySeat.class, name = "NO_LONELY_SEAT")
})
public sealed interface PurchasePolicyDTO {

    @JsonProperty("type")
    String type();

    IEventPurchasePolicy toDomain();

    static PurchasePolicyDTO fromDomain(IEventPurchasePolicy policy) {
        if (policy instanceof MaxTicketsPerOrderPolicy m) {
            return new MaxTicketsPerOrder(m.max());
        }
        if (policy instanceof AgeRestrictionPolicy a) {
            return new AgeRestriction(a.minAge());
        }
        if (policy instanceof MinTicketsRule m) {
            return new MinTicketsPerOrder(m.min());
        }
        if (policy instanceof NoLonelySeatPolicy) {
            return new NoLonelySeat();
        }
        throw new IllegalArgumentException(
                "Unsupported purchase policy type for wire format: " + policy.getClass().getName());
    }

    record MaxTicketsPerOrder(int max) implements PurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MAX_TICKETS_PER_ORDER";
        }

        @Override
        public IEventPurchasePolicy toDomain() {
            return new MaxTicketsPerOrderPolicy(max);
        }
    }

    record AgeRestriction(int minAge) implements PurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "AGE_RESTRICTION";
        }

        @Override
        public IEventPurchasePolicy toDomain() {
            return new AgeRestrictionPolicy(minAge);
        }
    }

    record MinTicketsPerOrder(int min) implements PurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MIN_TICKETS_PER_ORDER";
        }

        @Override
        public IEventPurchasePolicy toDomain() {
            return new MinTicketsRule(min);
        }
    }

    record NoLonelySeat() implements PurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "NO_LONELY_SEAT";
        }

        @Override
        public IEventPurchasePolicy toDomain() {
            return new NoLonelySeatPolicy();
        }
    }
}
