package com.asvarishch.jackpot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


@Component
public class JsonConfigHelper {

    private final ObjectMapper objectMapper;

    public JsonConfigHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode readConfigJsonOrNull(String json) {
        if (isBlank(json)) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    public BigDecimal getDecimal(JsonNode root, String field) {
        if (root == null || field == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        JsonNode node = root.get(field);
        try {
            if (node.isNumber()) {
                return new BigDecimal(node.asText());
            } else if (node.isTextual()) {
                String s = node.asText();
                if (isBlank(s)) return null;
                return new BigDecimal(s.trim());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
