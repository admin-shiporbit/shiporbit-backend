package com.shiporbit.backend.rate.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class DirectionSetConverter implements AttributeConverter<Set<ShipmentDirection>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ShipmentDirection> set) {
        return set.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<ShipmentDirection> convertToEntityAttribute(String csv) {
        EnumSet<ShipmentDirection> set = EnumSet.noneOf(ShipmentDirection.class);
        Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(ShipmentDirection::valueOf).forEach(set::add);
        return set;
    }
}