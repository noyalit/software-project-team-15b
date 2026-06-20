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
 * names): one of {@code "MAX_TICKETS"}, {@code "MIN_TICKETS"}, {@code "MIN_AGE"} for the
 * leaf rules, or {@code "AND"} / {@code "OR"} for composites that nest a {@code children}
 * list of further policies.
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

    private static List<CompanyPurchasePolicyDTO> mapChildren(List<IPurchasePolicy> children) {
        return children.stream().map(child -> {
            if (child instanceof ICompanyPurchasePolicy companyChild) {
                return fromDomain(companyChild);
            }
            throw new IllegalArgumentException(
                    "Unsupported purchase policy child for wire format: " + child.getClass().getName());
        }).toList();
    }

    private static List<IPurchasePolicy> toDomainChildren(List<CompanyPurchasePolicyDTO> children) {
        return children.stream().map(c -> (IPurchasePolicy) c.toDomain()).toList();
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
            return new AndPurchasePolicy(toDomainChildren(children));
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
            return new OrPurchasePolicy(toDomainChildren(children));
        }
    }
}
