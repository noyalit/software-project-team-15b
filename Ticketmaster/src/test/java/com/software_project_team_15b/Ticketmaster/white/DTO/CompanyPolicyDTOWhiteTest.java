package com.software_project_team_15b.Ticketmaster.white.DTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyPurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.EarlyBirdDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.AndPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against a regression where {@code GET /purchase-policies} and
 * {@code GET /discount-policies} would 4xx/5xx for companies whose stored chain held a
 * composite (And/Or, Sum/Max) or an early-bird discount: {@code fromDomain} used to throw
 * {@code IllegalArgumentException} for any subtype it did not enumerate. These cases must
 * now round-trip through the clean {@code "type"}-tagged wire format.
 */
class CompanyPolicyDTOWhiteTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void andCompositePurchasePolicyRoundTrips() {
        AndPurchasePolicy domain = new AndPurchasePolicy(List.of(
                new MinAgeRule(18), new MaxTicketsRule(4)));

        CompanyPurchasePolicyDTO dto = CompanyPurchasePolicyDTO.fromDomain(domain);
        assertEquals("AND", dto.type());

        ICompanyPurchasePolicy back = dto.toDomain();
        AndPurchasePolicy and = assertInstanceOf(AndPurchasePolicy.class, back);
        List<IPurchasePolicy> children = and.children();
        assertEquals(2, children.size());
        assertInstanceOf(MinAgeRule.class, children.get(0));
        assertInstanceOf(MaxTicketsRule.class, children.get(1));
    }

    @Test
    void orCompositePurchasePolicySerializesNestedTypeDiscriminators() throws Exception {
        OrPurchasePolicy domain = new OrPurchasePolicy(List.of(new MaxTicketsRule(2)));

        String json = mapper.writeValueAsString(CompanyPurchasePolicyDTO.fromDomain(domain));
        assertTrue(json.contains("\"type\":\"OR\""), json);
        assertTrue(json.contains("\"type\":\"MAX_TICKETS\""), json);

        CompanyPurchasePolicyDTO parsed = mapper.readValue(json, CompanyPurchasePolicyDTO.class);
        assertInstanceOf(OrPurchasePolicy.class, parsed.toDomain());
    }

    @Test
    void earlyBirdDiscountPolicyRoundTrips() {
        Instant until = Instant.parse("2026-07-01T00:00:00Z");
        EarlyBirdDiscountPolicy domain = new EarlyBirdDiscountPolicy(new BigDecimal("15"), until);

        CompanyDiscountPolicyDTO dto = CompanyDiscountPolicyDTO.fromDomain(domain);
        assertEquals("EARLY_BIRD", dto.type());

        EarlyBirdDiscountPolicy back = assertInstanceOf(EarlyBirdDiscountPolicy.class, dto.toDomain());
        assertEquals(0, new BigDecimal("15").compareTo(back.percentage()));
        assertEquals(until, back.until());
    }

    @Test
    void maxCompositeDiscountPolicyRoundTrips() {
        MaxDiscountPolicy domain = new MaxDiscountPolicy(List.of(
                new SimpleDiscountPolicy(new BigDecimal("10")),
                new EarlyBirdDiscountPolicy(new BigDecimal("15"), Instant.parse("2026-07-01T00:00:00Z"))));

        CompanyDiscountPolicyDTO dto = CompanyDiscountPolicyDTO.fromDomain(domain);
        assertEquals("MAX", dto.type());

        MaxDiscountPolicy back = assertInstanceOf(MaxDiscountPolicy.class, dto.toDomain());
        List<IDiscountPolicy> children = back.children();
        assertEquals(2, children.size());
        assertInstanceOf(SimpleDiscountPolicy.class, children.get(0));
        assertInstanceOf(EarlyBirdDiscountPolicy.class, children.get(1));
    }

    @Test
    void sumCompositeDiscountPolicySerializesNestedTypeDiscriminators() throws Exception {
        SumDiscountPolicy domain = new SumDiscountPolicy(List.of(
                new SimpleDiscountPolicy(new BigDecimal("10"))));

        String json = mapper.writeValueAsString(CompanyDiscountPolicyDTO.fromDomain(domain));
        assertTrue(json.contains("\"type\":\"SUM\""), json);
        assertTrue(json.contains("\"type\":\"SIMPLE\""), json);

        CompanyDiscountPolicyDTO parsed = mapper.readValue(json, CompanyDiscountPolicyDTO.class);
        ICompanyDiscountPolicy back = parsed.toDomain();
        assertInstanceOf(SumDiscountPolicy.class, back);
    }
}
