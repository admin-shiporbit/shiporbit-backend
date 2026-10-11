package com.shiporbit.backend.rate.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter
public class CsvStringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        return (list == null || list.isEmpty()) ? null : String.join(",", list);
    }

    @Override
    public List<String> convertToEntityAttribute(String csv) {
        return csv == null ? List.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}