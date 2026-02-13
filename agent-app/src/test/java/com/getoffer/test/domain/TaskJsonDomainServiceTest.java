package com.getoffer.test.domain;

import com.getoffer.domain.task.service.TaskJsonDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TaskJsonDomainServiceTest {

    private final TaskJsonDomainService service = new TaskJsonDomainService();

    @Test
    public void shouldParseStrictJsonObject() {
        Map<String, Object> payload = service.parseStrictJsonObject("{\"a\":1}", this::parseStub);
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(1, payload.get("a"));
    }

    @Test
    public void shouldParseEmbeddedJsonObject() {
        Map<String, Object> payload = service.parseEmbeddedJsonObject("text {\"a\":1} tail", this::parseStub);
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(1, payload.get("a"));
    }

    @Test
    public void shouldReturnNullWhenNoJsonFound() {
        Map<String, Object> payload = service.parseEmbeddedJsonObject("no json", this::parseStub);
        Assertions.assertNull(payload);
    }

    @Test
    public void shouldFallbackToStringWhenSerializerThrows() {
        String json = service.toJson(Map.of("a", 1), value -> {
            throw new IllegalStateException("boom");
        });
        Assertions.assertTrue(json.contains("a=1"));
    }

    private Map<String, Object> parseStub(String json) {
        if (json == null || !json.trim().startsWith("{") || !json.trim().endsWith("}")) {
            throw new IllegalStateException("invalid json");
        }
        Map<String, Object> payload = new HashMap<>();
        if (json.contains("\"a\":1") || json.contains("\"a\": 1")) {
            payload.put("a", 1);
        }
        return payload;
    }
}
