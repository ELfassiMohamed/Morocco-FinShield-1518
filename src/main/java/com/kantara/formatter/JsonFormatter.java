package com.kantara.formatter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kantara.exception.KantaraException;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonFormatter implements OutputFormatter {
    
    private final ObjectMapper mapper;

    public JsonFormatter() {
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Override
    public String format(Map<String, Object> extractedData, String sourceType) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source_type", sourceType);
            result.put("data", extractedData);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new KantaraException("Failed to format data as JSON", e);
        }
    }

    @Override
    public String formatName() {
        return "json";
    }
}
