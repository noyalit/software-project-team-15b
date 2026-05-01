package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

public final class PolicyJsonConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<List<IEventPurchasePolicy>> PURCHASE_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<IEventDiscountPolicy>> DISCOUNT_LIST_TYPE = new TypeReference<>() {};

    private PolicyJsonConverter() {}

    @Converter(autoApply = false)
    public static class PurchasePolicyListConverter implements AttributeConverter<List<IEventPurchasePolicy>, String> {
        @Override
        public String convertToDatabaseColumn(List<IEventPurchasePolicy> attribute) {
            if (attribute == null || attribute.isEmpty()) return null;
            try { return MAPPER.writerFor(PURCHASE_LIST_TYPE).writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize purchase policies", e); }
        }

        @Override
        public List<IEventPurchasePolicy> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try { return MAPPER.readValue(dbData, PURCHASE_LIST_TYPE); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize purchase policies", e); }
        }
    }

    @Converter(autoApply = false)
    public static class DiscountPolicyListConverter implements AttributeConverter<List<IEventDiscountPolicy>, String> {
        @Override
        public String convertToDatabaseColumn(List<IEventDiscountPolicy> attribute) {
            if (attribute == null || attribute.isEmpty()) return null;
            try { return MAPPER.writerFor(DISCOUNT_LIST_TYPE).writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize discount policies", e); }
        }

        @Override
        public List<IEventDiscountPolicy> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try { return MAPPER.readValue(dbData, DISCOUNT_LIST_TYPE); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize discount policies", e); }
        }
    }
}
