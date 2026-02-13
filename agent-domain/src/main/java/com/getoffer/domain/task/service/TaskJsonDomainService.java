package com.getoffer.domain.task.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;

/**
 * Task JSON 领域服务：负责 JSON 解析与序列化兜底策略。
 */
@Service
public class TaskJsonDomainService {

    public Map<String, Object> parseEmbeddedJsonObject(String text,
                                                       Function<String, Map<String, Object>> strictParser) {
        if (isBlank(text) || strictParser == null) {
            return null;
        }

        String trimmed = text.trim();
        Map<String, Object> parsed = parseStrict(trimmed, strictParser);
        if (parsed != null) {
            return parsed;
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String snippet = trimmed.substring(start, end + 1);
        return parseStrict(snippet, strictParser);
    }

    public Map<String, Object> parseStrictJsonObject(String text,
                                                     Function<String, Map<String, Object>> strictParser) {
        if (isBlank(text) || strictParser == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        return parseStrict(trimmed, strictParser);
    }

    public String toJson(Object value, Function<Object, String> serializer) {
        if (value == null) {
            return "{}";
        }
        if (serializer == null) {
            return String.valueOf(value);
        }
        try {
            String serialized = serializer.apply(value);
            return serialized == null ? String.valueOf(value) : serialized;
        } catch (RuntimeException ex) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> parseStrict(String text,
                                            Function<String, Map<String, Object>> strictParser) {
        try {
            return strictParser.apply(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
