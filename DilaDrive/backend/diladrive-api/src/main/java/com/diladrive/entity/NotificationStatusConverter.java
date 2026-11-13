package com.diladrive.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NotificationStatusConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean read) {
        return Boolean.TRUE.equals(read) ? "READ" : "UNREAD";
    }

    @Override
    public Boolean convertToEntityAttribute(String status) {
        return status != null && status.equalsIgnoreCase("READ");
    }
}
