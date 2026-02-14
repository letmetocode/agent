package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.application.command.SopSpecCompileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SopSpecCompileServiceTest {

    @Test
    public void shouldCompileSopSpecToRuntimeGraph() {
        SopSpecCompileService service = new SopSpecCompileService(new ObjectMapper());

        Map<String, Object> stepA = new LinkedHashMap<>();
        stepA.put("id", "research");
        stepA.put("name", "资料检索");
        stepA.put("roleType", "WORKER");

        Map<String, Object> stepB = new LinkedHashMap<>();
        stepB.put("id", "review");
        stepB.put("name", "结论评审");
        stepB.put("roleType", "CRITIC");
        stepB.put("dependsOn", List.of("research"));
        stepB.put("failurePolicy", "failFast");

        Map<String, Object> sopSpec = new LinkedHashMap<>();
        sopSpec.put("steps", List.of(stepA, stepB));

        SopSpecCompileService.CompileResult result = service.compile(sopSpec);
        Assertions.assertNotNull(result.compileHash());
        Assertions.assertNotNull(result.nodeSignature());
        Assertions.assertEquals(2, ((List<?>) result.sopRuntimeGraph().get("nodes")).size());
        Assertions.assertEquals(1, ((List<?>) result.sopRuntimeGraph().get("edges")).size());
        Assertions.assertEquals(2, ((Number) result.sopRuntimeGraph().get("version")).intValue());
    }

    @Test
    public void shouldRejectCycleDependency() {
        SopSpecCompileService service = new SopSpecCompileService(new ObjectMapper());

        Map<String, Object> stepA = new LinkedHashMap<>();
        stepA.put("id", "a");
        stepA.put("dependsOn", List.of("b"));

        Map<String, Object> stepB = new LinkedHashMap<>();
        stepB.put("id", "b");
        stepB.put("dependsOn", List.of("a"));

        Map<String, Object> sopSpec = new LinkedHashMap<>();
        sopSpec.put("steps", List.of(stepA, stepB));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> service.compile(sopSpec));
        Assertions.assertTrue(ex.getMessage().contains("循环依赖"));
    }

    @Test
    public void shouldInjectImplicitGroupForStepGroupId() {
        SopSpecCompileService service = new SopSpecCompileService(new ObjectMapper());

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "a");
        step.put("groupId", "g1");

        Map<String, Object> sopSpec = new LinkedHashMap<>();
        sopSpec.put("steps", List.of(step));

        SopSpecCompileService.CompileResult result = service.compile(sopSpec);
        List<?> groups = (List<?>) result.sopRuntimeGraph().get("groups");
        Assertions.assertEquals(1, groups.size());
        Assertions.assertFalse(result.warnings().isEmpty());
    }
}
