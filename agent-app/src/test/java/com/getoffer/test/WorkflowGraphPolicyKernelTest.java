package com.getoffer.test;

import com.getoffer.domain.planning.service.WorkflowGraphPolicyKernel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowGraphPolicyKernelTest {

    @Test
    public void shouldNormalizeGraphAndExpandGroupEdges() {
        Map<String, Object> nodeA = new LinkedHashMap<>();
        nodeA.put("id", "a");
        nodeA.put("type", "WORKER");

        Map<String, Object> nodeB = new LinkedHashMap<>();
        nodeB.put("id", "b");
        nodeB.put("type", "CRITIC");

        Map<String, Object> nodeC = new LinkedHashMap<>();
        nodeC.put("id", "c");
        nodeC.put("type", "WORKER");

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", "g1");
        group.put("nodes", List.of("a", "b"));
        group.put("joinPolicy", "any");

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", "g1");
        edge.put("to", "c");

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("version", 1);
        graph.put("nodes", List.of(nodeA, nodeB, nodeC));
        graph.put("groups", List.of(group));
        graph.put("edges", List.of(edge));

        Map<String, Object> normalized = WorkflowGraphPolicyKernel.normalizeAndValidateGraphDefinition(
                graph,
                "候选草案",
                true,
                null
        );

        Assertions.assertEquals(WorkflowGraphPolicyKernel.GRAPH_DSL_VERSION, normalized.get("version"));
        List<?> edges = (List<?>) normalized.get("edges");
        Assertions.assertEquals(2, edges.size());
    }

    @Test
    public void shouldRejectInvalidJoinPolicyInStrictMode() {
        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowGraphPolicyKernel.normalizeJoinPolicyStrict("xxx", "sopSpec.steps[0].joinPolicy")
        );
        Assertions.assertTrue(ex.getMessage().contains("仅支持all/any/quorum"));
    }

    @Test
    public void shouldResolveNodePolicyOverGroupPolicy() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", "g1");
        group.put("joinPolicy", "any");
        group.put("failurePolicy", "failFast");

        Map<String, Map<String, Object>> groupPolicyById = WorkflowGraphPolicyKernel.buildGroupPolicyById(List.of(group));

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "n1");
        node.put("type", "WORKER");
        node.put("groupId", "g1");
        node.put("joinPolicy", "quorum");

        Map<String, Object> policy = WorkflowGraphPolicyKernel.resolveGraphPolicyForNode(node, groupPolicyById);
        Assertions.assertEquals("quorum", policy.get("joinPolicy"));
        Assertions.assertEquals("failFast", policy.get("failurePolicy"));
    }
}
