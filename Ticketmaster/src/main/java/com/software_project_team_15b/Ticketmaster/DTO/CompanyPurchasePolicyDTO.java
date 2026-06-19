package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;

/**
 * Transport-layer representation of a company purchase policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java class
 * names): one of {@code "MAX_TICKETS"}, {@code "MIN_TICKETS"}, {@code "MIN_AGE"}.
 *
 * <p>Examples:
 * <pre>
 *   { "type": "MAX_TICKETS", "max": 4 }
 *   { "type": "MIN_TICKETS", "min": 2 }
 *   { "type": "MIN_AGE", "minAge": 18 }
 * </pre>
 *
 * <p>The discriminator uses {@link JsonTypeInfo.As#EXISTING_PROPERTY} backed by a real
 * {@code type} accessor so it is always serialized — including when the value travels
 * through a generic wrapper such as {@code ApiResponse<List<...>>}, where Jackson would
 * otherwise omit a synthesized type id.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.MaxTickets.class, name = "MAX_TICKETS"),
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.MinTickets.class, name = "MIN_TICKETS"),
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.MinAge.class, name = "MIN_AGE")
})
public sealed interface CompanyPurchasePolicyDTO {

    @JsonProperty("type")
    String type();

    ICompanyPurchasePolicy toDomain();

    static CompanyPurchasePolicyDTO fromDomain(ICompanyPurchasePolicy policy) {
        if (policy instanceof MaxTicketsRule m) {
            return new MaxTickets(m.max());
        }
        if (policy instanceof MinTicketsRule m) {
            return new MinTickets(m.min());
        }
        if (policy instanceof MinAgeRule a) {
            return new MinAge(a.minAge());
        }
        throw new IllegalArgumentException(
                "Unsupported company purchase policy type for wire format: " + policy.getClass().getName());
    }

    record MaxTickets(int max) implements CompanyPurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MAX_TICKETS";
        }

        @Override
        public ICompanyPurchasePolicy toDomain() {
            return new MaxTicketsRule(max);
        }
    }

    record MinTickets(int min) implements CompanyPurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MIN_TICKETS";
        }

        @Override
        public ICompanyPurchasePolicy toDomain() {
            return new MinTicketsRule(min);
        }
    }

    record MinAge(int minAge) implements CompanyPurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "MIN_AGE";
        }

        @Override
        public ICompanyPurchasePolicy toDomain() {
            return new MinAgeRule(minAge);
        }
    }
}
