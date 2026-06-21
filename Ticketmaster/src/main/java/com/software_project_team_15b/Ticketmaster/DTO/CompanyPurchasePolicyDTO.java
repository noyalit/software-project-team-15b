package com.software_project_team_15b.Ticketmaster.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.AndPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;

import java.util.List;

/**
 * Transport-layer representation of a company purchase policy.
 *
 * <p>Wire format uses a clean {@code "type"} discriminator field (no Java class
 * names): one of {@code "MAX_TICKETS"}, {@code "MIN_TICKETS"}, {@code "MIN_AGE"} (leaves),
 * or {@code "AND"}, {@code "OR"} (composite roots that carry a {@code children} list).
 *
 * <p>Examples:
 * <pre>
 *   { "type": "MAX_TICKETS", "max": 4 }
 *   { "type": "MIN_TICKETS", "min": 2 }
 *   { "type": "MIN_AGE", "minAge": 18 }
 *   { "type": "AND", "children": [ { "type": "MIN_AGE", "minAge": 18 }, { "type": "MAX_TICKETS", "max": 4 } ] }
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
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.MinAge.class, name = "MIN_AGE"),
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.And.class, name = "AND"),
        @JsonSubTypes.Type(value = CompanyPurchasePolicyDTO.Or.class, name = "OR")
})
public sealed interface CompanyPurchasePolicyDTO {

    @JsonProperty("type")
    String type();

    ICompanyPurchasePolicy toDomain();

    static CompanyPurchasePolicyDTO fromDomain(ICompanyPurchasePolicy policy) {
        if (policy instanceof AndPurchasePolicy a) {
            return new And(a.children().stream().map(CompanyPurchasePolicyDTO::fromChild).toList());
        }
        if (policy instanceof OrPurchasePolicy o) {
            return new Or(o.children().stream().map(CompanyPurchasePolicyDTO::fromChild).toList());
        }
        if (policy instanceof MaxTicketsRule m) {
            return new MaxTickets(m.max());
        }
        if (policy instanceof MinTicketsRule m) {
            return new MinTickets(m.min());
        }
        if (policy instanceof MinAgeRule a) {
            return new MinAge(a.minAge());
        }
        if (policy instanceof AndPurchasePolicy and) {
            return new And(mapChildren(and.children()));
        }
        if (policy instanceof OrPurchasePolicy or) {
            return new Or(mapChildren(or.children()));
        }
        throw new IllegalArgumentException(
                "Unsupported company purchase policy type for wire format: " + policy.getClass().getName());
    }

    /**
     * Maps a composite child back to its DTO. Children of a persisted company purchase
     * root are themselves company purchase policies (leaves or nested composites), so
     * the cast holds; a non-company child surfaces as the same "unsupported" error.
     */
    private static CompanyPurchasePolicyDTO fromChild(IPurchasePolicy child) {
        if (child instanceof ICompanyPurchasePolicy c) {
            return fromDomain(c);
        }
        throw new IllegalArgumentException(
                "Unsupported company purchase policy type for wire format: " + child.getClass().getName());
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

    record And(List<CompanyPurchasePolicyDTO> children) implements CompanyPurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "AND";
        }

        @Override
        public ICompanyPurchasePolicy toDomain() {
            return new AndPurchasePolicy(children.stream()
                    .map(CompanyPurchasePolicyDTO::toDomain)
                    .map(p -> (IPurchasePolicy) p)
                    .toList());
        }
    }

    record Or(List<CompanyPurchasePolicyDTO> children) implements CompanyPurchasePolicyDTO {
        @Override
        @JsonProperty("type")
        public String type() {
            return "OR";
        }

        @Override
        public ICompanyPurchasePolicy toDomain() {
            return new OrPurchasePolicy(children.stream()
                    .map(CompanyPurchasePolicyDTO::toDomain)
                    .map(p -> (IPurchasePolicy) p)
                    .toList());
        }
    }
}
