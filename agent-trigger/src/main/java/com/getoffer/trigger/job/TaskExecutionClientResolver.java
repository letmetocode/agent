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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        Map<String, Object> toolPolicy = resolveToolPolicy(task);
        String effectiveSystemPrompt = buildToolPolicyAwareSystemPrompt(systemPromptSuffix, toolPolicy);
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                taskAgentSelectionDomainService.resolveSelectionPlan(task, workerFallbackAgentKeys, criticFallbackAgentKeys);
        String conversationId = buildConversationId(plan, task);
        TaskAgentSelectionDomainService.ClientSelectionResult<ChatClient> selected =
                taskAgentSelectionDomainService.resolveClient(
                        selectionPlan,
                        agentId -> agentFactory.createAgent(agentId, conversationId, effectiveSystemPrompt, toolPolicy),
                        agentKey -> agentFactory.createAgent(agentKey, conversationId, effectiveSystemPrompt, toolPolicy),
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
        if (!toolPolicy.isEmpty()) {
            log.info("Task tool policy enforced. taskId={}, nodeId={}, mode={}, allowed={}, blocked={}",
                    task == null ? null : task.getId(),
                    task == null ? null : task.getNodeId(),
                    resolveMode(toolPolicy),
                    resolveToolList(toolPolicy, "allowedToolNames", "allowedTools", "allowlist", "allowList"),
                    resolveToolList(toolPolicy, "blockedToolNames", "blockedTools", "blocklist", "blockList"));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveToolPolicy(AgentTaskEntity task) {
        if (task == null || task.getConfigSnapshot() == null || task.getConfigSnapshot().isEmpty()) {
            return Collections.emptyMap();
        }
        Object value = task.getConfigSnapshot().get("toolPolicy");
        if (value == null) {
            value = task.getConfigSnapshot().get("tool_policy");
        }
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Collections.emptyMap();
    }

    private String buildToolPolicyAwareSystemPrompt(String originSuffix, Map<String, Object> toolPolicy) {
        if (toolPolicy == null || toolPolicy.isEmpty()) {
            return originSuffix;
        }
        String mode = resolveMode(toolPolicy);
        String allowed = resolveToolList(toolPolicy, "allowedToolNames", "allowedTools", "allowlist", "allowList");
        String blocked = resolveToolList(toolPolicy, "blockedToolNames", "blockedTools", "blocklist", "blockList");
        StringBuilder policyPrompt = new StringBuilder();
        policyPrompt.append("工具调用策略：mode=").append(mode);
        if (!allowed.isEmpty()) {
            policyPrompt.append("；仅允许工具=").append(allowed);
        }
        if (!blocked.isEmpty()) {
            policyPrompt.append("；禁止工具=").append(blocked);
        }
        policyPrompt.append("。请严格遵守工具白名单/黑名单约束，不得调用未授权工具。");

        if (StringUtils.isBlank(originSuffix)) {
            return policyPrompt.toString();
        }
        return originSuffix + "\n" + policyPrompt;
    }

    private String resolveMode(Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) {
            return "allowAll";
        }
        Object mode = policy.get("mode");
        if (mode == null) {
            mode = policy.get("policyMode");
        }
        return mode == null ? "allowAll" : String.valueOf(mode).trim().toLowerCase(Locale.ROOT);
    }

    private String resolveToolList(Map<String, Object> policy, String... keys) {
        if (policy == null || policy.isEmpty() || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = policy.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item != null)
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(item -> !item.isEmpty())
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
            }
            if (value instanceof String text) {
                return text.trim();
            }
        }
        return "";
    }
}
