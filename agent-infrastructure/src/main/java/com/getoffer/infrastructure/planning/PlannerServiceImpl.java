package com.getoffer.infrastructure.planning;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.ISopTemplateRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.SopTemplateEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Planner service implementation.
 *
 * @author getoffer
 * @since 2026-02-02
 */
@Slf4j
@Service
public class PlannerServiceImpl implements PlannerService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_PRIORITY = 0;

    private final ISopTemplateRepository sopTemplateRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final JsonCodec jsonCodec;

    public PlannerServiceImpl(ISopTemplateRepository sopTemplateRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec) {
        this.sopTemplateRepository = sopTemplateRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public SopTemplateEntity matchSop(String userQuery) {
        if (StringUtils.isBlank(userQuery)) {
            return null;
        }
        List<SopTemplateEntity> templates = sopTemplateRepository.findByActive(true);
        if (templates == null || templates.isEmpty()) {
            return null;
        }

        String query = userQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> tokens = tokenize(query);

        SopTemplateEntity best = null;
        double bestScore = 0;
        for (SopTemplateEntity template : templates) {
            if (template == null || !Boolean.TRUE.equals(template.getIsActive())) {
                continue;
            }
            String trigger = StringUtils.defaultString(template.getTriggerDescription()).toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(trigger)) {
                continue;
            }
            double score = computeScore(query, tokens, trigger);
            if (score > bestScore) {
                best = template;
                bestScore = score;
                continue;
            }
            if (score == bestScore && score > 0 && best != null) {
                Integer currentVersion = template.getVersion();
                Integer bestVersion = best.getVersion();
                if (currentVersion != null && bestVersion != null && currentVersion > bestVersion) {
                    best = template;
                }
            }
        }
        return bestScore > 0 ? best : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanEntity createPlan(Long sessionId, String userQuery) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId is required");
        }
        if (StringUtils.isBlank(userQuery)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "User query is required");
        }

        SopTemplateEntity sop = matchSop(userQuery);
        if (sop == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "No SOP matched for query");
        }
        Map<String, Object> executionGraph = deepCopyMap(sop.getGraphDefinition());
        if (executionGraph.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SOP graph definition is empty");
        }

        Map<String, Object> userInput = parseUserInput(userQuery);
        validateInput(sop.getInputSchema(), userInput);

        Map<String, Object> globalContext = buildGlobalContext(sessionId, userQuery, sop, userInput);

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setSessionId(sessionId);
        plan.setSopTemplateId(sop.getId());
        plan.setPlanGoal(userQuery);
        plan.setExecutionGraph(executionGraph);
        plan.setGlobalContext(globalContext);
        plan.setStatus(PlanStatusEnum.PLANNING);
        plan.setPriority(resolvePriority(sop.getDefaultConfig()));
        plan.setVersion(0);

        AgentPlanEntity savedPlan = agentPlanRepository.save(plan);

        List<AgentTaskEntity> tasks = unfoldGraph(savedPlan, sop, executionGraph, globalContext);
        if (tasks.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "No executable nodes found in SOP graph");
        }
        agentTaskRepository.batchSave(tasks);

        savedPlan.ready();
        return agentPlanRepository.update(savedPlan);
    }

    private double computeScore(String query, Set<String> tokens, String trigger) {
        if (trigger.contains(query)) {
            return 100D;
        }
        if (query.contains(trigger)) {
            return 100D;
        }
        if (tokens == null || tokens.isEmpty()) {
            return 0D;
        }
        int hits = 0;
        for (String token : tokens) {
            if (StringUtils.isBlank(token)) {
                continue;
            }
            if (trigger.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private Set<String> tokenize(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.isNotBlank(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? Collections.emptyMap() : copy;
    }

    private Map<String, Object> parseUserInput(String userQuery) {
        if (StringUtils.isBlank(userQuery)) {
            return Collections.emptyMap();
        }
        String trimmed = userQuery.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> parsed = jsonCodec.readMap(trimmed);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.debug("Failed to parse userQuery as JSON: {}", ex.getMessage());
            }
        }
        Map<String, Object> input = new HashMap<>();
        input.put("userQuery", userQuery);
        input.put("query", userQuery);
        return input;
    }

    private void validateInput(Map<String, Object> inputSchema, Map<String, Object> userInput) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }
        Object required = inputSchema.get("required");
        if (!(required instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) required) {
            String key = item == null ? null : String.valueOf(item);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = userInput == null ? null : userInput.get(key);
            if (value == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Missing required input: " + key);
            }
            if (value instanceof String && StringUtils.isBlank((String) value)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Missing required input: " + key);
            }
        }
    }

    private Map<String, Object> buildGlobalContext(Long sessionId,
                                                   String userQuery,
                                                   SopTemplateEntity sop,
                                                   Map<String, Object> userInput) {
        Map<String, Object> context = new HashMap<>();
        if (userInput != null) {
            context.putAll(userInput);
        }
        context.put("userQuery", userQuery);
        context.put("sessionId", sessionId);
        if (sop != null) {
            context.put("sopTemplateId", sop.getId());
            context.put("sopCategory", sop.getCategory());
            context.put("sopName", sop.getName());
            context.put("sopVersion", sop.getVersion());
        }
        return context;
    }

    private Integer resolvePriority(Map<String, Object> defaultConfig) {
        Integer priority = getInteger(defaultConfig, "priority");
        return priority != null ? priority : DEFAULT_PRIORITY;
    }

    private List<AgentTaskEntity> unfoldGraph(AgentPlanEntity plan,
                                              SopTemplateEntity sop,
                                              Map<String, Object> executionGraph,
                                              Map<String, Object> globalContext) {
        List<Map<String, Object>> nodes = getMapList(executionGraph, "nodes");
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> edges = getMapList(executionGraph, "edges");
        Map<String, List<String>> dependencies = buildDependencies(edges);

        Map<String, Object> defaultConfig = sop == null ? null : sop.getDefaultConfig();
        List<AgentTaskEntity> tasks = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || node.isEmpty()) {
                continue;
            }
            String nodeId = getString(node, "id", "nodeId", "node_id");
            if (StringUtils.isBlank(nodeId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Node id is required");
            }
            String taskType = getString(node, "type", "taskType", "task_type");
            if (StringUtils.isBlank(taskType)) {
                taskType = "WORKER";
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

    private Integer resolveMaxRetries(Map<String, Object> config) {
        Integer value = getInteger(config, "max_retries", "maxRetries", "maxRetry");
        return value != null ? value : DEFAULT_MAX_RETRIES;
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
            if (value instanceof Number) {
                return ((Number) value).intValue();
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
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
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
}
