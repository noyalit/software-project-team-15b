package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.IDiscountCondition;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MaxTicketsCondition;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MinTicketsCondition;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.TimeWindowCondition;

import java.time.Instant;

/**
 * Transport-layer representation of the precondition attached to a conditional company
 * discount. Wire format uses a clean {@code "type"} discriminator: one of
 * {@code "MAX_TICKETS"}, {@code "MIN_TICKETS"}, {@code "TIME_WINDOW"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "MAX_TICKETS", "max": 4 }
 *   { "type": "MIN_TICKETS", "min": 2 }
 *   { "type": "TIME_WINDOW", "from": "2026-06-01T00:00:00Z", "to": "2026-07-01T00:00:00Z" }
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompanyDiscountConditionDTO.MaxTickets.class, name = "MAX_TICKETS"),
        @JsonSubTypes.Type(value = CompanyDiscountConditionDTO.MinTickets.class, name = "MIN_TICKETS"),
        @JsonSubTypes.Type(value = CompanyDiscountConditionDTO.TimeWindow.class, name = "TIME_WINDOW")
})
public sealed interface CompanyDiscountConditionDTO {

    @JsonProperty("type")
    String type();

    IDiscountCondition toDomain();

    static CompanyDiscountConditionDTO fromDomain(IDiscountCondition condition) {
        if (condition instanceof MaxTicketsCondition c) {
            return new MaxTickets(c.max());
        }
        if (condition instanceof MinTicketsCondition c) {
            return new MinTickets(c.min());
        }
        if (condition instanceof TimeWindowCondition c) {
            return new TimeWindow(c.from(), c.to());
        }
        throw new IllegalArgumentException(
                "Unsupported discount condition type for wire format: " + condition.getClass().getName());
    }

    record MaxTickets(int max) implements CompanyDiscountConditionDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MAX_TICKETS";
        }

        @Override
        public IDiscountCondition toDomain() {
            return new MaxTicketsCondition(max);
        }
    }

    record MinTickets(int min) implements CompanyDiscountConditionDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MIN_TICKETS";
        }

        @Override
        public IDiscountCondition toDomain() {
            return new MinTicketsCondition(min);
        }
    }

    record TimeWindow(Instant from, Instant to) implements CompanyDiscountConditionDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "TIME_WINDOW";
        }

        @Override
        public IDiscountCondition toDomain() {
            return new TimeWindowCondition(from, to);
        }
    }
}
