package com.getoffer.trigger.job;

import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaskExecutionClientResolverTest {

    @Test
    public void shouldPropagateToolPolicyToAgentFactoryAndPromptSuffix() {
        IAgentFactory agentFactory = mock(IAgentFactory.class);
        IAgentRegistryRepository agentRegistryRepository = mock(IAgentRegistryRepository.class);
        TaskAgentSelectionDomainService selectionDomainService = new TaskAgentSelectionDomainService();
        TaskExecutionClientResolver resolver = new TaskExecutionClientResolver(
                agentFactory,
                agentRegistryRepository,
                selectionDomainService,
                List.of("assistant"),
                List.of("assistant"),
                10_000L
        );

        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(101L);
        task.setNodeId("node-1");
        task.setTaskType(TaskTypeEnum.WORKER);

        Map<String, Object> toolPolicy = new HashMap<>();
        toolPolicy.put("mode", "allowlist");
        toolPolicy.put("allowedToolNames", List.of("web_search", "calculator"));

        Map<String, Object> config = new HashMap<>();
        config.put("agentKey", "assistant");
        config.put("toolPolicy", toolPolicy);
        task.setConfigSnapshot(config);

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(8L);

        ChatClient expectedClient = mock(ChatClient.class);
        when(agentFactory.createAgent(eq("assistant"), eq("plan-8:node-1"), anyString(), anyMap()))
                .thenReturn(expectedClient);

        ChatClient actualClient = resolver.resolveTaskClient(task, plan, "retry:1");

        Assertions.assertSame(expectedClient, actualClient);

        ArgumentCaptor<String> suffixCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> policyCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(agentFactory)
                .createAgent(eq("assistant"), eq("plan-8:node-1"), suffixCaptor.capture(), policyCaptor.capture());

        Assertions.assertTrue(suffixCaptor.getValue().contains("工具调用策略"));
        Assertions.assertEquals("allowlist", String.valueOf(policyCaptor.getValue().get("mode")));
        Assertions.assertEquals(List.of("web_search", "calculator"), policyCaptor.getValue().get("allowedToolNames"));
    }
}
