package com.getoffer.infrastructure.planning;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.WorkflowGraphPolicyKernel;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 图节点 -> AgentTask 物化服务。
 */
public class WorkflowTaskMaterializationService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private final JsonCodec jsonCodec;

    public WorkflowTaskMaterializationService(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public List<AgentTaskEntity> materializeTasks(AgentPlanEntity plan,
                                                  Map<String, Object> defaultConfig,
                                                  Map<String, Object> workflowToolPolicy,
                                                  Map<String, Object> executionGraph,
                                                  Map<String, Object> globalContext) {
        List<Map<String, Object>> nodes = getMapList(executionGraph, "nodes");
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> edges = getMapList(executionGraph, "edges");
        Map<String, List<String>> dependencies = buildDependencies(edges);
        Map<String, Map<String, Object>> groupPolicyById = WorkflowGraphPolicyKernel.buildGroupPolicyById(
                getMapList(executionGraph, "groups")
        );

        List<AgentTaskEntity> tasks = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || node.isEmpty()) {
                continue;
            }
            String nodeId = getString(node, "id", "nodeId", "node_id");
            if (StringUtils.isBlank(nodeId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Node id is required");
            }
            String rawTaskType = getString(node, "type", "taskType", "task_type");
            TaskTypeEnum taskType = TaskTypeEnum.WORKER;
            if (StringUtils.isNotBlank(rawTaskType)) {
                try {
                    taskType = TaskTypeEnum.fromText(rawTaskType);
                } catch (IllegalArgumentException ex) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                            "Unsupported task type: " + rawTaskType);
                }
            }
            String name = getString(node, "name", "title", "label");
            List<String> deps = dependencies.getOrDefault(nodeId, Collections.emptyList());

            AgentTaskEntity task = new AgentTaskEntity();
            task.setPlanId(plan.getId());
            task.setNodeId(nodeId);
            task.setName(StringUtils.defaultIfBlank(name, nodeId));
            task.setTaskType(taskType);
            task.setStatus(TaskStatusEnum.PENDING);
            task.setDependencyNodeIds(new ArrayList<>(deps));
            task.setInputContext(globalContext == null ? new HashMap<>() : new HashMap<>(globalContext));

            Map<String, Object> configSnapshot = mergeConfig(defaultConfig, node);
            configSnapshot.put("graphPolicy", WorkflowGraphPolicyKernel.resolveGraphPolicyForNode(node, groupPolicyById));
            Map<String, Object> effectiveToolPolicy = resolveEffectiveToolPolicy(workflowToolPolicy, configSnapshot);
            if (!effectiveToolPolicy.isEmpty()) {
                configSnapshot.put("toolPolicy", effectiveToolPolicy);
            }
            task.setConfigSnapshot(configSnapshot);
            task.setMaxRetries(resolveMaxRetries(configSnapshot));
            task.setCurrentRetry(0);
            task.setVersion(0);
            tasks.add(task);
        }
        return tasks;
    }

    private Map<String, List<String>> buildDependencies(List<Map<String, Object>> edges) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> dependencies = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            if (edge == null || edge.isEmpty()) {
                continue;
            }
            String from = getString(edge, "from", "source", "src");
            String to = getString(edge, "to", "target", "dst");
            if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
                continue;
            }
            dependencies.computeIfAbsent(to, key -> new ArrayList<>()).add(from);
        }
        return dependencies;
    }

    private Map<String, Object> mergeConfig(Map<String, Object> defaultConfig, Map<String, Object> node) {
        Map<String, Object> merged = new HashMap<>();
        if (defaultConfig != null) {
            merged.putAll(defaultConfig);
        }
        Map<String, Object> nodeConfig = getMap(node, "config", "configSnapshot", "config_snapshot", "options");
        if (nodeConfig != null) {
            merged.putAll(nodeConfig);
        }
        return merged;
    }

    private Map<String, Object> resolveEffectiveToolPolicy(Map<String, Object> workflowToolPolicy,
                                                           Map<String, Object> configSnapshot) {
        Map<String, Object> effective = normalizeToolPolicy(workflowToolPolicy);
        Map<String, Object> nodeToolPolicy = normalizeToolPolicy(getMap(configSnapshot, "toolPolicy", "tool_policy"));
        if (!nodeToolPolicy.isEmpty()) {
            effective.putAll(nodeToolPolicy);
        }
        List<String> allowList = readStringList(effective,
                "allowedToolNames", "allowedTools", "allowlist", "allowList", "whitelist");
        List<String> blockList = readStringList(effective,
                "blockedToolNames", "blockedTools", "blocklist", "blockList", "denylist");
        if (!allowList.isEmpty()) {
            effective.put("allowedToolNames", allowList);
        }
        if (!blockList.isEmpty()) {
            effective.put("blockedToolNames", blockList);
        }
        return effective;
    }

    private Map<String, Object> normalizeToolPolicy(Map<String, Object> rawPolicy) {
        if (rawPolicy == null || rawPolicy.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> normalized = deepCopyMap(rawPolicy);
        String mode = getString(normalized, "mode", "policyMode", "policy_mode");
        if (StringUtils.isBlank(mode)) {
            mode = "allowAll";
        }
        normalized.put("mode", mode.trim());
        return normalized;
    }

    private Integer resolveMaxRetries(Map<String, Object> config) {
        Integer value = getInteger(config, "max_retries", "maxRetries", "maxRetry");
        return value != null ? value : DEFAULT_MAX_RETRIES;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
    }

    private Integer getInteger(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMapList(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty() || StringUtils.isBlank(key)) {
            return Collections.emptyList();
        }
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value instanceof Map<?, ?>) {
                return (Map<String, Object>) value;
            }
        }
        return null;
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private List<String> readStringList(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (!(value instanceof List<?> list)) {
                continue;
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text;
                if (item instanceof Map<?, ?> itemMap) {
                    Object nodeId = itemMap.get("id");
                    if (nodeId == null) {
                        nodeId = itemMap.get("nodeId");
                    }
                    if (nodeId == null) {
                        nodeId = itemMap.get("node_id");
                    }
                    text = nodeId == null ? null : String.valueOf(nodeId).trim();
                } else {
                    text = String.valueOf(item).trim();
                }
                if (StringUtils.isNotBlank(text)) {
                    result.add(text);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
