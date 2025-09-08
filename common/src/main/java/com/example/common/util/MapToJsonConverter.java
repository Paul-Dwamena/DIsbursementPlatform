package com.example.common.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.util.Map;

@WritingConverter
public class MapToJsonConverter implements Converter<Map<String, Object>, Json> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Json convert(Map<String, Object> source) {
        try {
            return Json.of(mapper.writeValueAsString(source));
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Map to Json", e);
        }
    }
}
