package com.nevgiu.hrai.security.audit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SecurityEventOutcomeConverter implements AttributeConverter<SecurityEventOutcome, String> {
    @Override
    public String convertToDatabaseColumn(SecurityEventOutcome value) {
        return value == null ? null : value.name();
    }

    @Override
    public SecurityEventOutcome convertToEntityAttribute(String value) {
        return value == null ? null : SecurityEventOutcome.valueOf(value);
    }
}
