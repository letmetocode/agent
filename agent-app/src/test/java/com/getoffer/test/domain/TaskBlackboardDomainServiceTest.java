package com.getoffer.test.domain;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TaskBlackboardDomainServiceTest {

    private final TaskBlackboardDomainService service = new TaskBlackboardDomainService();

    @Test
    public void shouldMergeJsonOutputWhenMergeEnabled() {
        AgentTaskEntity task = new AgentTaskEntity();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeOutput", true);
        task.setConfigSnapshot(config);

        Map<String, Object> delta = service.buildContextDelta(task, "{\"k\":\"v\"}", text -> {
            Map<String, Object> parsed = new HashMap<>();
            parsed.put("k", "v");
            return parsed;
        });

        Assertions.assertEquals("v", delta.get("k"));
        Assertions.assertEquals(1, delta.size());
    }

    @Test
    public void shouldFallbackToOutputKeyWhenMergeDisabled() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setNodeId("node-a");
        Map<String, Object> config = new HashMap<>();
        config.put("output_key", "result");
        task.setConfigSnapshot(config);

        Map<String, Object> delta = service.buildContextDelta(task, "output-text", text -> null);

        Assertions.assertEquals("output-text", delta.get("result"));
    }

    @Test
    public void shouldFallbackToNodeIdWhenNoOutputKeyConfigured() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setNodeId("node-b");
        task.setConfigSnapshot(new HashMap<>());

        Map<String, Object> delta = service.buildContextDelta(task, "x", text -> null);

        Assertions.assertEquals("x", delta.get("node-b"));
    }

    @Test
    public void shouldMergeContextWithDelta() {
        Map<String, Object> current = new HashMap<>();
        current.put("a", 1);
        Map<String, Object> delta = new HashMap<>();
        delta.put("b", 2);

        Map<String, Object> merged = service.mergeContext(current, delta);

        Assertions.assertEquals(1, merged.get("a"));
        Assertions.assertEquals(2, merged.get("b"));
    }
}
