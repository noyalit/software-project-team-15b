package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.NoLonelySeatPolicy;

/**
 * Transport-layer representation of an event purchase policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java
 * class names): one of
 * {@code "MAX_TICKETS_PER_ORDER"}, {@code "AGE_RESTRICTION"},
 * {@code "NO_LONELY_SEAT"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "MAX_TICKETS_PER_ORDER", "max": 4 }
 *   { "type": "AGE_RESTRICTION", "minAge": 18 }
 *   { "type": "NO_LONELY_SEAT" }
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PurchasePolicyDTO.MaxTicketsPerOrder.class, name = "MAX_TICKETS_PER_ORDER"),
        @JsonSubTypes.Type(value = PurchasePolicyDTO.AgeRestriction.class, name = "AGE_RESTRICTION"),
        @JsonSubTypes.Type(value = PurchasePolicyDTO.NoLonelySeat.class, name = "NO_LONELY_SEAT")
})
public sealed interface PurchasePolicyDTO {

    IEventPurchasePolicy toDomain();

    record MaxTicketsPerOrder(int max) implements PurchasePolicyDTO {
        @Override
        public IEventPurchasePolicy toDomain() {
            return new MaxTicketsPerOrderPolicy(max);
        }
    }

    record AgeRestriction(int minAge) implements PurchasePolicyDTO {
        @Override
        public IEventPurchasePolicy toDomain() {
            return new AgeRestrictionPolicy(minAge);
        }
    }

    record NoLonelySeat() implements PurchasePolicyDTO {
        @Override
        public IEventPurchasePolicy toDomain() {
            return new NoLonelySeatPolicy();
        }
    }
}
