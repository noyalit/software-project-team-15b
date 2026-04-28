package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public final class PolicyJsonConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private PolicyJsonConverter() {}

    @Converter(autoApply = false)
    public static class PurchasePolicyConverter implements AttributeConverter<IEventPurchasePolicy, String> {
        @Override
        public String convertToDatabaseColumn(IEventPurchasePolicy attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize purchase policy", e); }
        }

        @Override
        public IEventPurchasePolicy convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return null;
            try { return MAPPER.readValue(dbData, IEventPurchasePolicy.class); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize purchase policy", e); }
        }
    }

    @Converter(autoApply = false)
    public static class DiscountPolicyConverter implements AttributeConverter<IEventDiscountPolicy, String> {
        @Override
        public String convertToDatabaseColumn(IEventDiscountPolicy attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize discount policy", e); }
        }

        @Override
        public IEventDiscountPolicy convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return null;
            try { return MAPPER.readValue(dbData, IEventDiscountPolicy.class); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize discount policy", e); }
        }
    }
}
