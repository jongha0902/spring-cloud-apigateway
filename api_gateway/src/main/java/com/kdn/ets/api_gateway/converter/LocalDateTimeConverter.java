package com.kdn.ets.api_gateway.converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    // 'YYYY-MM-DD HH:MM:SS' 형식에 맞는 포맷터를 정의합니다.
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        // LocalDateTime 객체 -> DB에 저장될 문자열로 변환
        return (attribute == null ? null : attribute.format(FORMATTER));
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        // DB의 문자열 -> LocalDateTime 객체로 변환
        return (dbData == null ? null : LocalDateTime.parse(dbData, FORMATTER));
    }
}