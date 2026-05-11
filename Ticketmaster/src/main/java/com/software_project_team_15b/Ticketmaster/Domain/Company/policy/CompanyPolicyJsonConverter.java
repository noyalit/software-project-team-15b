package com.software_project_team_15b.Ticketmaster.Domain.Company.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

public final class CompanyPolicyJsonConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<List<ICompanyPurchasePolicy>> PURCHASE_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ICompanyDiscountPolicy>> DISCOUNT_LIST_TYPE = new TypeReference<>() {};

    private CompanyPolicyJsonConverter() {}

    @Converter(autoApply = false)
    public static class PurchasePolicyListConverter implements AttributeConverter<List<ICompanyPurchasePolicy>, String> {
        @Override
        public String convertToDatabaseColumn(List<ICompanyPurchasePolicy> attribute) {
            if (attribute == null || attribute.isEmpty()) return null;
            try { return MAPPER.writerFor(PURCHASE_LIST_TYPE).writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize company purchase policies", e); }
        }

        @Override
        public List<ICompanyPurchasePolicy> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new java.util.ArrayList<>();
            try { return MAPPER.readValue(dbData, PURCHASE_LIST_TYPE); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize company purchase policies", e); }
        }
    }

    @Converter(autoApply = false)
    public static class DiscountPolicyListConverter implements AttributeConverter<List<ICompanyDiscountPolicy>, String> {
        @Override
        public String convertToDatabaseColumn(List<ICompanyDiscountPolicy> attribute) {
            if (attribute == null || attribute.isEmpty()) return null;
            try { return MAPPER.writerFor(DISCOUNT_LIST_TYPE).writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalStateException("failed to serialize company discount policies", e); }
        }

        @Override
        public List<ICompanyDiscountPolicy> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new java.util.ArrayList<>();
            try { return MAPPER.readValue(dbData, DISCOUNT_LIST_TYPE); }
            catch (Exception e) { throw new IllegalStateException("failed to deserialize company discount policies", e); }
        }
    }
}
