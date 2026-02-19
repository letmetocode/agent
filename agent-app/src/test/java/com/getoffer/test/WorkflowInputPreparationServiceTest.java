package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.infrastructure.planning.WorkflowInputPreparationService;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowInputPreparationServiceTest {

    @Test
    public void shouldParseJsonUserInput() {
        WorkflowInputPreparationService service = new WorkflowInputPreparationService(new JsonCodec(new ObjectMapper()));
        Map<String, Object> input = service.parseUserInput("{\"productName\":\"测试商品\"}");
        Assertions.assertEquals("测试商品", input.get("productName"));
    }

    @Test
    public void shouldEnrichRequiredInputFromQueryAndContext() {
        WorkflowInputPreparationService service = new WorkflowInputPreparationService(new JsonCodec(new ObjectMapper()));
        Map<String, Object> schema = new HashMap<>();
        schema.put("required", List.of("productName", "channel"));
        schema.put("properties", Map.of(
                "productName", Map.of("type", "string"),
                "channel", Map.of("type", "string")
        ));

        Map<String, Object> userInput = service.parseUserInput("商品名称是 可乐");
        service.enrichRequiredInputFromQuery(schema, userInput, "商品名称是 可乐");

        Map<String, Object> globalContext = new HashMap<>();
        globalContext.put("Channel", "douyin");
        service.enrichRequiredInputFromContext(schema, userInput, globalContext);
        service.validateInput(schema, userInput);

        Assertions.assertEquals("可乐", userInput.get("productName"));
        Assertions.assertEquals("douyin", userInput.get("channel"));
    }

    @Test
    public void shouldThrowWhenRequiredMissing() {
        WorkflowInputPreparationService service = new WorkflowInputPreparationService(new JsonCodec(new ObjectMapper()));
        Map<String, Object> schema = new HashMap<>();
        schema.put("required", List.of("productName"));
        Map<String, Object> userInput = new HashMap<>();
        AppException ex = Assertions.assertThrows(AppException.class, () -> service.validateInput(schema, userInput));
        Assertions.assertTrue(ex.getInfo().contains("Missing required input"));
    }
}
