package com.software_project_team_15b.Ticketmaster.white.DTO;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;

/**
 * Regression: event purchase/discount policies must serialize their {@code type}
 * discriminator even when carried inside a generic wrapper such as
 * {@code ApiResponse<List<...>>}. With a synthesized type id ({@code As.PROPERTY})
 * Jackson dropped {@code type} through the generic erasure, so the GET response was
 * missing it and the round-tripped PUT failed with
 * "missing type id property 'type'". The DTOs now expose a real {@code type()} accessor
 * ({@code As.EXISTING_PROPERTY}) so the field is always present.
 */
class EventPolicyDTOWireFormatTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void purchasePolicies_keepTypeThroughGenericWrapper() throws Exception {
        List<PurchasePolicyDTO> policies = List.of(
                new PurchasePolicyDTO.MaxTicketsPerOrder(4),
                new PurchasePolicyDTO.AgeRestriction(18),
                new PurchasePolicyDTO.MinTicketsPerOrder(2),
                new PurchasePolicyDTO.NoLonelySeat());

        String json = mapper.writeValueAsString(new ApiResponse<>(policies, null));

        assertThat(json)
                .contains("MAX_TICKETS_PER_ORDER")
                .contains("AGE_RESTRICTION")
                .contains("MIN_TICKETS_PER_ORDER")
                .contains("NO_LONELY_SEAT");

        // The serialized response must deserialize back as a valid PUT body (the exact
        // round-trip the UI performs when seeding its draft from the GET response).
        ApiResponse<List<PurchasePolicyDTO>> back = mapper.readValue(
                json, new TypeReference<ApiResponse<List<PurchasePolicyDTO>>>() {});
        assertThat(back.getData())
                .extracting(PurchasePolicyDTO::type)
                .containsExactly("MAX_TICKETS_PER_ORDER", "AGE_RESTRICTION",
                        "MIN_TICKETS_PER_ORDER", "NO_LONELY_SEAT");
    }

    @Test
    void discountPolicies_keepTypeThroughGenericWrapper() throws Exception {
        List<DiscountPolicyDTO> policies = List.of(
                new DiscountPolicyDTO.Coupon("SUMMER25", new BigDecimal("25"), Instant.parse("2026-08-31T23:59:59Z")),
                new DiscountPolicyDTO.EarlyBird(new BigDecimal("10"), Instant.parse("2026-06-01T00:00:00Z")));

        String json = mapper.writeValueAsString(new ApiResponse<>(policies, null));

        assertThat(json).contains("COUPON").contains("EARLY_BIRD");

        ApiResponse<List<DiscountPolicyDTO>> back = mapper.readValue(
                json, new TypeReference<ApiResponse<List<DiscountPolicyDTO>>>() {});
        assertThat(back.getData())
                .extracting(DiscountPolicyDTO::type)
                .containsExactly("COUPON", "EARLY_BIRD");
    }
}
