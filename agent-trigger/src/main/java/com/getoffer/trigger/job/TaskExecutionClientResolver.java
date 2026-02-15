package com.getoffer.trigger.job;

import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * 单任务执行客户端解析组件：负责 TaskClient 选路与默认 Agent 缓存。
 */
@Slf4j
final class TaskExecutionClientResolver {

    private final IAgentFactory agentFactory;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final TaskAgentSelectionDomainService taskAgentSelectionDomainService;
    private final List<String> workerFallbackAgentKeys;
    private final List<String> criticFallbackAgentKeys;
    private final long defaultAgentCacheTtlMs;
    private volatile AgentRegistryEntity cachedDefaultAgent;
    private volatile long cachedDefaultAgentAtMillis;

    TaskExecutionClientResolver(IAgentFactory agentFactory,
                                IAgentRegistryRepository agentRegistryRepository,
                                TaskAgentSelectionDomainService taskAgentSelectionDomainService,
                                List<String> workerFallbackAgentKeys,
                                List<String> criticFallbackAgentKeys,
                                long defaultAgentCacheTtlMs) {
        this.agentFactory = agentFactory;
        this.agentRegistryRepository = agentRegistryRepository;
        this.taskAgentSelectionDomainService = taskAgentSelectionDomainService;
        this.workerFallbackAgentKeys = workerFallbackAgentKeys;
        this.criticFallbackAgentKeys = criticFallbackAgentKeys;
        this.defaultAgentCacheTtlMs = defaultAgentCacheTtlMs;
        this.cachedDefaultAgent = null;
        this.cachedDefaultAgentAtMillis = 0L;
    }

    ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix) {
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                taskAgentSelectionDomainService.resolveSelectionPlan(task, workerFallbackAgentKeys, criticFallbackAgentKeys);
        String conversationId = buildConversationId(plan, task);
        TaskAgentSelectionDomainService.ClientSelectionResult<ChatClient> selected =
                taskAgentSelectionDomainService.resolveClient(
                        selectionPlan,
                        agentId -> agentFactory.createAgent(agentId, conversationId, systemPromptSuffix),
                        agentKey -> agentFactory.createAgent(agentKey, conversationId, systemPromptSuffix),
                        this::resolveDefaultActiveAgentProfile
                );
        if (selected.source() == TaskAgentSelectionDomainService.ClientSelectionSource.FALLBACK_AGENT_KEY
                && taskAgentSelectionDomainService.shouldWarnFallbackKey(selected.selectedAgentKey())) {
            log.warn("Task fallback agent profile key applied. taskId={}, nodeId={}, taskType={}, agentKey={}",
                    task.getId(), task.getNodeId(), task.getTaskType(), selected.selectedAgentKey());
        } else if (selected.source() == TaskAgentSelectionDomainService.ClientSelectionSource.DEFAULT_ACTIVE_AGENT) {
            log.warn("Task fallback to first active agent profile. taskId={}, nodeId={}, taskType={}, agentKey={}",
                    task.getId(), task.getNodeId(), task.getTaskType(), selected.selectedAgentKey());
        }
        if (selected.source() == TaskAgentSelectionDomainService.ClientSelectionSource.UNAVAILABLE
                || selected.client() == null) {
            throw new IllegalStateException("No available active agent profile for task fallback. triedKeys="
                    + taskAgentSelectionDomainService.joinAgentKeys(selected.attemptedAgentKeys()));
        }
        return selected.client();
    }

    private AgentRegistryEntity resolveDefaultActiveAgentProfile() {
        long now = System.currentTimeMillis();
        AgentRegistryEntity cached = cachedDefaultAgent;
        if (cached != null && now - cachedDefaultAgentAtMillis <= defaultAgentCacheTtlMs) {
            return cached;
        }
        List<AgentRegistryEntity> activeAgents = agentRegistryRepository.findByActive(true);
        if (activeAgents == null || activeAgents.isEmpty()) {
            cachedDefaultAgent = null;
            cachedDefaultAgentAtMillis = now;
            return null;
        }
        AgentRegistryEntity selected = taskAgentSelectionDomainService.selectDefaultActiveAgent(activeAgents);
        cachedDefaultAgent = selected;
        cachedDefaultAgentAtMillis = now;
        return selected;
    }

    private String buildConversationId(AgentPlanEntity plan, AgentTaskEntity task) {
        String planPart = plan.getId() == null ? "plan" : "plan-" + plan.getId();
        String nodePart = StringUtils.defaultIfBlank(task.getNodeId(), "node");
        return planPart + ":" + nodePart;
    }
}
