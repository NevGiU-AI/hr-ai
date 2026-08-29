package com.nevgiu.hrai.security.audit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SecurityEventTypeConverter implements AttributeConverter<SecurityEventType, String> {
    @Override
    public String convertToDatabaseColumn(SecurityEventType value) {
        return value == null ? null : value.name();
    }

    @Override
    public SecurityEventType convertToEntityAttribute(String value) {
        return value == null ? null : SecurityEventType.valueOf(value);
    }
}
