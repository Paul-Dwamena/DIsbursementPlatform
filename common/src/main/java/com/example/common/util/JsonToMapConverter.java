package com.example.common.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.Map;

@ReadingConverter
public class JsonToMapConverter implements Converter<Json, Map<String, Object>> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> convert(Json source) {
        try {
            return mapper.readValue(source.asString(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Json to Map", e);
        }
    }
}

