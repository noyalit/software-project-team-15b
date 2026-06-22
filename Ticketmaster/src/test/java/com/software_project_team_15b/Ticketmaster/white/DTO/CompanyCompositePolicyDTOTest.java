package com.software_project_team_15b.Ticketmaster.white.DTO;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.DTO.CompanyDiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyPurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.SumDiscountPolicy;

/**
 * Regression for code-review finding #1: composite policy roots must map to the wire DTO
 * (instead of throwing → HTTP 400), and round-trip back to an equivalent domain tree.
 */
class CompanyCompositePolicyDTOTest {

    @Test
    void maxDiscountRoot_mapsAndRoundTrips() {
        var root = new MaxDiscountPolicy(List.of(
                new SimpleDiscountPolicy(new BigDecimal("10")),
                new SimpleDiscountPolicy(new BigDecimal("20"))));

        CompanyDiscountPolicyDTO dto = CompanyDiscountPolicyDTO.fromDomain(root);
        assertThat(dto.type()).isEqualTo("MAX");

        ICompanyDiscountPolicy back = dto.toDomain();
        assertThat(back).isInstanceOf(MaxDiscountPolicy.class);
        assertThat(((MaxDiscountPolicy) back).children())
                .extracting(c -> ((SimpleDiscountPolicy) c).percent())
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"));
    }

    @Test
    void sumDiscountRoot_mapsAndRoundTrips() {
        var root = new SumDiscountPolicy(List.of(new SimpleDiscountPolicy(new BigDecimal("15"))));

        CompanyDiscountPolicyDTO dto = CompanyDiscountPolicyDTO.fromDomain(root);
        assertThat(dto.type()).isEqualTo("SUM");
        assertThat(dto.toDomain()).isInstanceOf(SumDiscountPolicy.class);
    }

    @Test
    void orPurchaseRoot_mapsAndRoundTrips() {
        var root = new OrPurchasePolicy(List.of(new MinAgeRule(18), new MaxTicketsRule(4)));

        CompanyPurchasePolicyDTO dto = CompanyPurchasePolicyDTO.fromDomain(root);
        assertThat(dto.type()).isEqualTo("OR");

        ICompanyPurchasePolicy back = dto.toDomain();
        assertThat(back).isInstanceOf(OrPurchasePolicy.class);
        assertThat(((OrPurchasePolicy) back).children()).hasSize(2);
    }
}
