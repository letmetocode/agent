package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.service.GraphDslPolicyService;
import com.getoffer.trigger.application.command.SopSpecCompileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphDslPolicyServiceTest {

    @Test
    public void shouldAlignCompileHashAndNodeSignatureWithPolicyService() {
        ObjectMapper objectMapper = new ObjectMapper();
        SopSpecCompileService compileService = new SopSpecCompileService(objectMapper);

        Map<String, Object> stepA = new LinkedHashMap<>();
        stepA.put("id", "collect");
        stepA.put("name", "收集信息");
        stepA.put("roleType", "WORKER");
        stepA.put("groupId", "g-main");

        Map<String, Object> stepB = new LinkedHashMap<>();
        stepB.put("id", "review");
        stepB.put("name", "质量复核");
        stepB.put("roleType", "CRITIC");
        stepB.put("dependsOn", List.of("collect"));
        stepB.put("groupId", "g-main");

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", "g-main");
        group.put("nodes", List.of("collect", "review"));
        group.put("joinPolicy", "all");

        Map<String, Object> sopSpec = new LinkedHashMap<>();
        sopSpec.put("steps", List.of(stepA, stepB));
        sopSpec.put("groups", List.of(group));

        SopSpecCompileService.CompileResult result = compileService.compile(sopSpec);
        Map<String, Object> runtimeGraph = result.sopRuntimeGraph();

        String policyHash = GraphDslPolicyService.hashGraph(runtimeGraph, objectMapper);
        String policyNodeSignature = GraphDslPolicyService.computeNodeSignature(runtimeGraph);

        Assertions.assertEquals(policyHash, result.compileHash());
        Assertions.assertEquals(policyNodeSignature, result.nodeSignature());
        GraphDslPolicyService.validateGraphDslV2(runtimeGraph, "graphDefinition");
    }

    @Test
    public void shouldRejectInvalidGraphDslVersion() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "n1");
        node.put("type", "WORKER");

        Map<String, Object> invalidGraph = new LinkedHashMap<>();
        invalidGraph.put("version", 1);
        invalidGraph.put("nodes", List.of(node));
        invalidGraph.put("groups", List.of());
        invalidGraph.put("edges", List.of());

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GraphDslPolicyService.validateGraphDslV2(invalidGraph, "graphDefinition")
        );
        Assertions.assertTrue(ex.getMessage().contains("version仅支持2"));
    }
}
