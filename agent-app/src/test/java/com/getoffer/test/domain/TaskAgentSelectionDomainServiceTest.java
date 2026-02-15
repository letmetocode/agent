package com.getoffer.test.domain;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskAgentSelectionDomainServiceTest {

    private final TaskAgentSelectionDomainService service = new TaskAgentSelectionDomainService();

    @Test
    public void shouldResolveConfiguredAgentIdAndKey() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskType(TaskTypeEnum.WORKER);
        Map<String, Object> config = new HashMap<>();
        config.put("agent_id", 1001L);
        config.put("agentKey", "assistant");
        task.setConfigSnapshot(config);

        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                service.resolveSelectionPlan(task, List.of("worker"), List.of("critic"));

        Assertions.assertEquals(1001L, selectionPlan.configuredAgentId());
        Assertions.assertEquals("assistant", selectionPlan.configuredAgentKey());
        Assertions.assertEquals(List.of("worker"), selectionPlan.fallbackKeys());
    }

    @Test
    public void shouldUseCriticFallbackKeysForCriticTask() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskType(TaskTypeEnum.CRITIC);
        task.setConfigSnapshot(new HashMap<>());

        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                service.resolveSelectionPlan(task, List.of("worker", "assistant"), List.of("critic", "assistant"));

        Assertions.assertEquals(List.of("critic", "assistant"), selectionPlan.fallbackKeys());
    }

    @Test
    public void shouldParseFallbackAgentKeysAndRemoveBlank() {
        List<String> keys = service.parseFallbackAgentKeys(" worker , assistant ,, ", "worker");
        Assertions.assertEquals(List.of("worker", "assistant"), keys);
    }

    @Test
    public void shouldSelectDefaultActiveAgentByMinId() {
        AgentRegistryEntity first = new AgentRegistryEntity();
        first.setId(3L);
        first.setKey("assistant");

        AgentRegistryEntity second = new AgentRegistryEntity();
        second.setId(1L);
        second.setKey("worker");

        AgentRegistryEntity selected = service.selectDefaultActiveAgent(Arrays.asList(first, second));

        Assertions.assertEquals(1L, selected.getId());
        Assertions.assertEquals("worker", selected.getKey());
    }

    @Test
    public void shouldIgnoreKnownAgentCreationErrors() {
        Assertions.assertTrue(service.isIgnorableCreateError(new IllegalStateException("Agent not found: xxx")));
        Assertions.assertTrue(service.isIgnorableCreateError(new IllegalStateException("Agent is inactive: xxx")));
        Assertions.assertFalse(service.isIgnorableCreateError(new IllegalStateException("other")));
    }

    @Test
    public void shouldResolveClientByFallbackAgentKey() {
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                new TaskAgentSelectionDomainService.SelectionPlan(null, null, List.of("worker", "assistant"));

        TaskAgentSelectionDomainService.ClientSelectionResult<String> result = service.resolveClient(
                selectionPlan,
                id -> "id-" + id,
                key -> {
                    if ("worker".equals(key)) {
                        throw new IllegalStateException("Agent not found: worker");
                    }
                    return "key-" + key;
                },
                () -> null
        );

        Assertions.assertEquals(TaskAgentSelectionDomainService.ClientSelectionSource.FALLBACK_AGENT_KEY, result.source());
        Assertions.assertEquals("assistant", result.selectedAgentKey());
        Assertions.assertEquals("key-assistant", result.client());
        Assertions.assertEquals(List.of("worker", "assistant"), result.attemptedAgentKeys());
    }

    @Test
    public void shouldFallbackToDefaultActiveAgentWhenFallbackKeysUnavailable() {
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                new TaskAgentSelectionDomainService.SelectionPlan(null, null, List.of("worker"));

        AgentRegistryEntity defaultAgent = new AgentRegistryEntity();
        defaultAgent.setId(9L);
        defaultAgent.setKey("assistant");

        TaskAgentSelectionDomainService.ClientSelectionResult<String> result = service.resolveClient(
                selectionPlan,
                id -> "id-" + id,
                key -> {
                    if ("worker".equals(key)) {
                        throw new IllegalStateException("Agent is inactive: worker");
                    }
                    return "key-" + key;
                },
                () -> defaultAgent
        );

        Assertions.assertEquals(TaskAgentSelectionDomainService.ClientSelectionSource.DEFAULT_ACTIVE_AGENT, result.source());
        Assertions.assertEquals("assistant", result.selectedAgentKey());
        Assertions.assertEquals(9L, result.selectedAgentId());
        Assertions.assertEquals("key-assistant", result.client());
        Assertions.assertEquals(List.of("worker", "assistant"), result.attemptedAgentKeys());
    }

    @Test
    public void shouldReturnUnavailableWhenNoFallbackAgentResolved() {
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                new TaskAgentSelectionDomainService.SelectionPlan(null, null, List.of("worker"));

        AgentRegistryEntity defaultAgent = new AgentRegistryEntity();
        defaultAgent.setId(10L);
        defaultAgent.setKey("assistant");

        TaskAgentSelectionDomainService.ClientSelectionResult<String> result = service.resolveClient(
                selectionPlan,
                id -> "id-" + id,
                key -> {
                    throw new IllegalStateException("Agent not found: " + key);
                },
                () -> defaultAgent
        );

        Assertions.assertEquals(TaskAgentSelectionDomainService.ClientSelectionSource.UNAVAILABLE, result.source());
        Assertions.assertNull(result.client());
        Assertions.assertEquals(List.of("worker", "assistant"), result.attemptedAgentKeys());
        Assertions.assertEquals("worker,assistant", service.joinAgentKeys(result.attemptedAgentKeys()));
    }
}
