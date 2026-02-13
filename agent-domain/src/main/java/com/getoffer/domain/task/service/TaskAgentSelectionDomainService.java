package com.getoffer.domain.task.service;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskTypeEnum;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Task Agent 选择领域服务：负责配置优先级、fallback 顺序与默认 Agent 选择策略。
 */
@Service
public class TaskAgentSelectionDomainService {

    public SelectionPlan resolveSelectionPlan(AgentTaskEntity task,
                                              List<String> workerFallbackAgentKeys,
                                              List<String> criticFallbackAgentKeys) {
        Map<String, Object> config = task == null ? null : task.getConfigSnapshot();
        Long configuredAgentId = getLong(config, "agentId", "agent_id");
        String configuredAgentKey = getString(config, "agentKey", "agent_key");

        List<String> fallbackKeys = task != null && task.getTaskType() == TaskTypeEnum.CRITIC
                ? safeCopy(criticFallbackAgentKeys)
                : safeCopy(workerFallbackAgentKeys);

        return new SelectionPlan(configuredAgentId, configuredAgentKey, fallbackKeys);
    }

    public List<String> parseFallbackAgentKeys(String configuredKeys, String... defaults) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (isNotBlank(configuredKeys)) {
            String[] parts = configuredKeys.split(",");
            for (String part : parts) {
                if (isNotBlank(part)) {
                    keys.add(part.trim());
                }
            }
        }
        if (keys.isEmpty() && defaults != null) {
            for (String item : defaults) {
                if (isNotBlank(item)) {
                    keys.add(item.trim());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    public boolean shouldWarnFallbackKey(String fallbackKey) {
        if (isBlank(fallbackKey)) {
            return false;
        }
        return !"worker".equalsIgnoreCase(fallbackKey) && !"critic".equalsIgnoreCase(fallbackKey);
    }

    public boolean isIgnorableCreateError(IllegalStateException ex) {
        if (ex == null) {
            return false;
        }
        String message = defaultString(ex.getMessage());
        return message.startsWith("Agent not found:") || message.startsWith("Agent is inactive:");
    }

    public AgentRegistryEntity selectDefaultActiveAgent(List<AgentRegistryEntity> activeAgents) {
        if (activeAgents == null || activeAgents.isEmpty()) {
            return null;
        }
        return activeAgents.stream()
                .filter(agent -> agent != null && isNotBlank(agent.getKey()))
                .min(Comparator.comparing(AgentRegistryEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private String getString(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private List<String> safeCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copied = new ArrayList<>(source.size());
        for (String value : source) {
            if (isNotBlank(value)) {
                copied.add(value.trim());
            }
        }
        return copied;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record SelectionPlan(Long configuredAgentId,
                                String configuredAgentKey,
                                List<String> fallbackKeys) {
    }
}
