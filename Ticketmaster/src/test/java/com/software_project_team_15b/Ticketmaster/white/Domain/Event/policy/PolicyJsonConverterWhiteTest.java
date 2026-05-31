package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.PolicyJsonConverter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyJsonConverterWhiteTest {

    private final PolicyJsonConverter.PurchasePolicyListConverter purchase =
            new PolicyJsonConverter.PurchasePolicyListConverter();
    private final PolicyJsonConverter.DiscountPolicyListConverter discount =
            new PolicyJsonConverter.DiscountPolicyListConverter();

    @Test
    void GivenNullList_WhenConvertPurchaseToDatabaseColumn_ThenReturnsNull() {
        assertThat(purchase.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void GivenEmptyList_WhenConvertPurchaseToDatabaseColumn_ThenReturnsNull() {
        assertThat(purchase.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    void GivenNullColumn_WhenConvertPurchaseToEntity_ThenReturnsEmptyList() {
        assertThat(purchase.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void GivenBlankColumn_WhenConvertPurchaseToEntity_ThenReturnsEmptyList() {
        assertThat(purchase.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void GivenValidJson_WhenConvertPurchaseRoundTrip_ThenPolicyListRestored() {
        List<IEventPurchasePolicy> original = List.of(new MaxTicketsPerOrderPolicy(4));
        String json = purchase.convertToDatabaseColumn(original);
        assertThat(json).isNotNull();

        List<IEventPurchasePolicy> back = purchase.convertToEntityAttribute(json);
        assertThat(back).hasSize(1).first().isInstanceOf(MaxTicketsPerOrderPolicy.class);
    }

    @Test
    void GivenInvalidJson_WhenConvertPurchaseToEntity_ThenThrowsIllegalState() {
        assertThatThrownBy(() -> purchase.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenNullList_WhenConvertDiscountToDatabaseColumn_ThenReturnsNull() {
        assertThat(discount.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void GivenEmptyList_WhenConvertDiscountToDatabaseColumn_ThenReturnsNull() {
        assertThat(discount.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    void GivenNullColumn_WhenConvertDiscountToEntity_ThenReturnsEmptyList() {
        assertThat(discount.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void GivenBlankColumn_WhenConvertDiscountToEntity_ThenReturnsEmptyList() {
        assertThat(discount.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void GivenValidJson_WhenConvertDiscountRoundTrip_ThenPolicyListRestored() {
        List<IEventDiscountPolicy> original =
                List.of(new CouponDiscountPolicy("SUMMER20", new BigDecimal("20")));
        String json = discount.convertToDatabaseColumn(original);
        assertThat(json).isNotNull();

        List<IEventDiscountPolicy> back = discount.convertToEntityAttribute(json);
        assertThat(back).hasSize(1).first().isInstanceOf(CouponDiscountPolicy.class);
    }

    @Test
    void GivenInvalidJson_WhenConvertDiscountToEntity_ThenThrowsIllegalState() {
        assertThatThrownBy(() -> discount.convertToEntityAttribute("{broken"))
                .isInstanceOf(IllegalStateException.class);
    }
}
