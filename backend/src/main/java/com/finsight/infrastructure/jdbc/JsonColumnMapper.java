package com.finsight.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Component
public class JsonColumnMapper {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonColumnMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PGobject jsonb(Object value) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(objectMapper.writeValueAsString(value));
            return object;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize jsonb column", ex);
        }
    }

    public Map<String, Object> objectMap(String value) {
        try {
            if (value == null || value.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse jsonb map", ex);
        }
    }

    public Map<String, String> stringMap(String value) {
        return objectMap(value).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue())
                ));
    }

    public List<String> stringList(String value) {
        try {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse jsonb list", ex);
        }
    }

    public void validatePgobject() throws SQLException {
        new PGobject().setType("jsonb");
    }
}

