package com.getoffer.infrastructure.planning;

import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
import com.getoffer.domain.planning.model.valobj.RoutingDecisionResult;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import com.getoffer.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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
    private static final Pattern DESCRIPTION_FIXED_VALUE_PATTERN =
            Pattern.compile("(?:固定(?:值)?为|默认(?:值)?为|默认为)\\s*[\"'“”]?([^\"'“”，。；;\\n]+)");
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_PRIORITY = 0;
    private static final String DEFAULT_TENANT = "DEFAULT";
    private static final String DEFAULT_CREATOR = "SYSTEM";
    private static final String SOURCE_TYPE_AUTO_MISS_ROOT = "AUTO_MISS_ROOT";
    private static final String SOURCE_TYPE_AUTO_MISS_FALLBACK = "AUTO_MISS_FALLBACK";
    private static final Set<String> VIRTUAL_ENTRY_NODE_IDS = Set.of(
            "START", "BEGIN", "ENTRY", "ROOT_START", "SOURCE"
    );
    private static final Set<String> VIRTUAL_EXIT_NODE_IDS = Set.of(
            "END", "FINISH", "EXIT", "ROOT_END", "SINK"
    );

    private final IWorkflowDefinitionRepository workflowDefinitionRepository;
    private final IWorkflowDraftRepository workflowDraftRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final JsonCodec jsonCodec;
    private final IRootWorkflowDraftPlanner rootWorkflowDraftPlanner;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final boolean rootPlannerEnabled;
    private final String rootAgentKey;
    private final int rootMaxAttempts;
    private final long rootRetryBackoffMs;
    private final boolean fallbackSingleNodeEnabled;
    private final String fallbackAgentKey;

    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec) {
        this(workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                null,
                null,
                true,
                "root",
                3,
                300L,
                true,
                "assistant");
    }

    @Autowired
    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec,
                              IRootWorkflowDraftPlanner rootWorkflowDraftPlanner,
                              IAgentRegistryRepository agentRegistryRepository,
                              @Value("${planner.root.enabled:true}") boolean rootPlannerEnabled,
                              @Value("${planner.root.agent-key:root}") String rootAgentKey,
                              @Value("${planner.root.retry.max-attempts:3}") int rootMaxAttempts,
                              @Value("${planner.root.retry.backoff-ms:300}") long rootRetryBackoffMs,
                              @Value("${planner.root.fallback.single-node.enabled:true}") boolean fallbackSingleNodeEnabled,
                              @Value("${planner.root.fallback.agent-key:assistant}") String fallbackAgentKey) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowDraftRepository = workflowDraftRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.jsonCodec = jsonCodec;
        this.rootWorkflowDraftPlanner = rootWorkflowDraftPlanner;
        this.agentRegistryRepository = agentRegistryRepository;
        this.rootPlannerEnabled = rootPlannerEnabled;
        this.rootAgentKey = StringUtils.defaultIfBlank(rootAgentKey, "root");
        this.rootMaxAttempts = Math.max(rootMaxAttempts, 1);
        this.rootRetryBackoffMs = Math.max(rootRetryBackoffMs, 0L);
        this.fallbackSingleNodeEnabled = fallbackSingleNodeEnabled;
        this.fallbackAgentKey = StringUtils.defaultIfBlank(fallbackAgentKey, this.rootAgentKey);
    }

    @Override
    public RoutingDecisionResult route(String userQuery) {
        WorkflowDefinitionEntity definition = matchDefinition(userQuery, workflowDefinitionRepository.findProductionActive());
        RoutingDecisionResult result = new RoutingDecisionResult();
        result.setStrategy("TRIGGER_TOKEN_SCORE");
        if (definition != null) {
            result.setDecisionType(RoutingDecisionTypeEnum.HIT_PRODUCTION);
            result.setReason("PRODUCTION_DEFINITION_MATCHED");
            result.setScore(BigDecimal.ONE);
            result.setDefinitionId(definition.getId());
            result.setDefinitionKey(definition.getDefinitionKey());
            result.setDefinitionVersion(definition.getVersion());
        } else {
            result.setDecisionType(RoutingDecisionTypeEnum.CANDIDATE);
            result.setReason("PRODUCTION_DEFINITION_MISSED");
            result.setScore(BigDecimal.ZERO);
        }
        return result;
    }

    private WorkflowDefinitionEntity matchDefinition(String userQuery, List<WorkflowDefinitionEntity> definitions) {
        if (StringUtils.isBlank(userQuery)) {
            return null;
        }
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }

        String query = userQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> tokens = tokenize(query);

        WorkflowDefinitionEntity best = null;
        double bestScore = 0;
        for (WorkflowDefinitionEntity definition : definitions) {
            if (definition == null || !Boolean.TRUE.equals(definition.getIsActive())) {
                continue;
            }
            String trigger = StringUtils.defaultString(definition.getRouteDescription()).toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(trigger)) {
                continue;
            }
            double score = computeScore(query, tokens, trigger);
            if (score > bestScore) {
                best = definition;
                bestScore = score;
                continue;
            }
            if (score == bestScore && score > 0 && best != null) {
                Integer currentVersion = definition.getVersion();
                Integer bestVersion = best.getVersion();
                if (currentVersion != null && bestVersion != null && currentVersion > bestVersion) {
                    best = definition;
                }
            }
        }
        return bestScore > 0 ? best : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanEntity createPlan(Long sessionId, String userQuery) {
        return createPlan(sessionId, userQuery, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanEntity createPlan(Long sessionId, String userQuery, Map<String, Object> extraContext) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId is required");
        }
        if (StringUtils.isBlank(userQuery)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "User query is required");
        }

        RoutedWorkflow routedWorkflow = routeAndResolve(sessionId, userQuery, extraContext);
        Map<String, Object> executionGraph = deepCopyMap(routedWorkflow.graphDefinition);
        if (executionGraph.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Workflow graph definition is empty");
        }

        Map<String, Object> userInput = parseUserInput(userQuery);
        enrichRequiredInputFromQuery(routedWorkflow.inputSchema, userInput, userQuery);
        validateInput(routedWorkflow.inputSchema, userInput);

        Map<String, Object> globalContext = buildGlobalContext(sessionId, userQuery, routedWorkflow, userInput);
        if (extraContext != null && !extraContext.isEmpty()) {
            globalContext.putAll(extraContext);
        }

        RoutingDecisionEntity savedDecision = routingDecisionRepository.save(buildRoutingDecisionEntity(sessionId, extraContext, routedWorkflow));
        log.info("ROUTING_DECIDED sessionId={}, turnId={}, routingDecisionId={}, decisionType={}, strategy={}, reason={}, score={}, definitionId={}, draftId={}",
                sessionId,
                savedDecision == null ? null : savedDecision.getTurnId(),
                savedDecision == null ? null : savedDecision.getId(),
                routedWorkflow.decisionType == null ? null : routedWorkflow.decisionType.name(),
                routedWorkflow.strategy,
                routedWorkflow.reason,
                routedWorkflow.score,
                routedWorkflow.definition == null ? null : routedWorkflow.definition.getId(),
                routedWorkflow.draft == null ? null : routedWorkflow.draft.getId());

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setSessionId(sessionId);
        plan.setRouteDecisionId(savedDecision.getId());
        plan.setWorkflowDefinitionId(routedWorkflow.definition == null ? null : routedWorkflow.definition.getId());
        plan.setWorkflowDraftId(routedWorkflow.draft == null ? null : routedWorkflow.draft.getId());
        plan.setPlanGoal(userQuery);
        plan.setExecutionGraph(executionGraph);
        plan.setDefinitionSnapshot(buildDefinitionSnapshot(routedWorkflow));
        plan.setGlobalContext(globalContext);
        plan.setStatus(PlanStatusEnum.PLANNING);
        plan.setPriority(resolvePriority(routedWorkflow.defaultConfig));
        plan.setVersion(0);

        AgentPlanEntity savedPlan = agentPlanRepository.save(plan);

        List<AgentTaskEntity> tasks = unfoldGraph(savedPlan, routedWorkflow.defaultConfig, executionGraph, globalContext);
        if (tasks.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "No executable nodes found in workflow graph");
        }
        agentTaskRepository.batchSave(tasks);

        savedPlan.ready();
        return agentPlanRepository.update(savedPlan);
    }

    private RoutedWorkflow routeAndResolve(Long sessionId,
                                           String userQuery,
                                           Map<String, Object> extraContext) {
        WorkflowDefinitionEntity definition = matchDefinition(userQuery, workflowDefinitionRepository.findProductionActive());
        if (definition != null) {
            RoutedWorkflow routed = new RoutedWorkflow();
            routed.decisionType = RoutingDecisionTypeEnum.HIT_PRODUCTION;
            routed.reason = "PRODUCTION_DEFINITION_MATCHED";
            routed.strategy = "TRIGGER_TOKEN_SCORE";
            routed.score = BigDecimal.ONE;
            routed.definition = definition;
            routed.graphDefinition = deepCopyMap(definition.getGraphDefinition());
            routed.inputSchema = deepCopyMap(definition.getInputSchema());
            routed.defaultConfig = deepCopyMap(definition.getDefaultConfig());
            return routed;
        }

        WorkflowDraftEntity draft = loadOrCreateDraft(sessionId, userQuery, extraContext);
        RoutedWorkflow routed = new RoutedWorkflow();
        routed.decisionType = StringUtils.equals(SOURCE_TYPE_AUTO_MISS_FALLBACK, draft.getSourceType())
                ? RoutingDecisionTypeEnum.FALLBACK
                : RoutingDecisionTypeEnum.CANDIDATE;
        routed.reason = StringUtils.defaultIfBlank(draft.getSourceType(), "PRODUCTION_DEFINITION_MISSED");
        routed.strategy = "ROOT_DRAFT_OR_FALLBACK";
        routed.score = BigDecimal.ZERO;
        routed.draft = draft;
        routed.graphDefinition = deepCopyMap(draft.getGraphDefinition());
        routed.inputSchema = deepCopyMap(draft.getInputSchema());
        routed.defaultConfig = deepCopyMap(draft.getDefaultConfig());
        return routed;
    }

    private WorkflowDraftEntity loadOrCreateDraft(Long sessionId,
                                                  String userQuery,
                                                  Map<String, Object> extraContext) {
        if (!rootPlannerEnabled) {
            log.info("Root planner is disabled. use fallback single-node candidate. sessionId={}", sessionId);
            return persistOrReuseDraft(userQuery,
                    buildFallbackSingleNodeDraft(userQuery, "ROOT_PLANNER_DISABLED"),
                    SOURCE_TYPE_AUTO_MISS_FALLBACK);
        }
        if (rootWorkflowDraftPlanner == null) {
            log.warn("Root planner bean not available. use fallback single-node candidate. sessionId={}", sessionId);
            return persistOrReuseDraft(userQuery,
                    buildFallbackSingleNodeDraft(userQuery, "ROOT_PLANNER_MISSING"),
                    SOURCE_TYPE_AUTO_MISS_FALLBACK);
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= rootMaxAttempts; attempt++) {
            try {
                Map<String, Object> planningContext = buildRootPlanningContext(sessionId, userQuery, extraContext, attempt);
                RootWorkflowDraft draft = rootWorkflowDraftPlanner.planDraft(sessionId, userQuery, planningContext);
                return persistOrReuseDraft(userQuery, draft, SOURCE_TYPE_AUTO_MISS_ROOT);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Root candidate planning failed. sessionId={}, attempt={}/{}, reason={}",
                        sessionId, attempt, rootMaxAttempts, ex.getMessage());
                sleepBackoff(attempt);
            }
        }

        if (!fallbackSingleNodeEnabled) {
            String reason = lastError == null ? "unknown" : StringUtils.defaultIfBlank(lastError.getMessage(), "unknown");
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "Root规划失败且已重试" + rootMaxAttempts + "次: " + reason);
        }
        log.warn("Root candidate planning exhausted retries. fallback to single-node candidate. sessionId={}", sessionId);
        return persistOrReuseDraft(userQuery,
                buildFallbackSingleNodeDraft(userQuery, "ROOT_PLANNING_FAILED"),
                SOURCE_TYPE_AUTO_MISS_FALLBACK);
    }

    private Map<String, Object> buildRootPlanningContext(Long sessionId,
                                                         String userQuery,
                                                         Map<String, Object> extraContext,
                                                         int attempt) {
        Map<String, Object> context = new HashMap<>();
        context.put("sessionId", sessionId);
        context.put("userQuery", userQuery);
        context.put("attempt", attempt);
        context.put("maxAttempts", rootMaxAttempts);
        context.put("fallbackAgentKey", fallbackAgentKey);
        context.put("rootAgentKey", rootAgentKey);
        if (extraContext != null && !extraContext.isEmpty()) {
            context.put("extraContext", extraContext);
        }
        return context;
    }

    private WorkflowDraftEntity persistOrReuseDraft(String userQuery,
                                                    RootWorkflowDraft draft,
                                                    String sourceType) {
        RootWorkflowDraft normalized = normalizeAndValidateDraft(userQuery, draft);
        String dedupHash = buildDedupHash(userQuery, normalized);
        WorkflowDraftEntity existed = workflowDraftRepository.findLatestDraftByDedupHash(dedupHash);
        if (existed != null) {
            return existed;
        }

        WorkflowDraftEntity draftEntity = new WorkflowDraftEntity();
        draftEntity.setDraftKey("draft-" + UUID.randomUUID());
        draftEntity.setTenantId(DEFAULT_TENANT);
        draftEntity.setCategory(StringUtils.defaultIfBlank(normalized.getCategory(), "candidate"));
        draftEntity.setName(StringUtils.defaultIfBlank(normalized.getName(), buildCandidateName(userQuery)));
        draftEntity.setRouteDescription(StringUtils.defaultIfBlank(normalized.getRouteDescription(), userQuery));
        draftEntity.setGraphDefinition(deepCopyMap(normalized.getGraphDefinition()));
        draftEntity.setInputSchema(deepCopyMap(normalized.getInputSchema()));
        draftEntity.setDefaultConfig(deepCopyMap(normalized.getDefaultConfig()));
        draftEntity.setToolPolicy(deepCopyMap(normalized.getToolPolicy()));
        draftEntity.setConstraints(deepCopyMap(normalized.getConstraints()));
        draftEntity.setInputSchemaVersion(StringUtils.defaultIfBlank(normalized.getInputSchemaVersion(), "v1"));
        draftEntity.setNodeSignature(StringUtils.defaultIfBlank(normalized.getNodeSignature(),
                computeNodeSignature(draftEntity.getGraphDefinition())));
        draftEntity.setDedupHash(dedupHash);
        draftEntity.setSourceType(sourceType);
        draftEntity.setStatus(WorkflowDraftStatusEnum.DRAFT);
        draftEntity.setCreatedBy(DEFAULT_CREATOR);
        return workflowDraftRepository.save(draftEntity);
    }

    private String buildCandidateName(String userQuery) {
        String normalized = StringUtils.defaultIfBlank(userQuery, "candidate");
        normalized = normalized.trim();
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return "auto-" + normalized;
    }

    private Map<String, Object> buildCandidateDefaultConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("priority", DEFAULT_PRIORITY);
        config.put("maxRetries", DEFAULT_MAX_RETRIES);
        return config;
    }

    private Map<String, Object> buildCandidateToolPolicy() {
        Map<String, Object> policy = new HashMap<>();
        policy.put("mode", "restricted");
        policy.put("allowWriteTools", false);
        policy.put("allowTools", Collections.emptyList());
        return policy;
    }

    private RootWorkflowDraft buildFallbackSingleNodeDraft(String userQuery, String candidateReason) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName(buildCandidateName(userQuery));
        draft.setRouteDescription(userQuery);
        draft.setInputSchema(Collections.emptyMap());
        draft.setDefaultConfig(buildCandidateDefaultConfig());
        draft.setToolPolicy(buildCandidateToolPolicy());
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("mode", "candidate-restricted");
        constraints.put("fallbackReason", candidateReason);
        draft.setConstraints(constraints);
        draft.setInputSchemaVersion("v1");
        draft.setNodeSignature("candidate-fallback-single-worker");
        draft.setGraphDefinition(buildCandidateGraph(userQuery, candidateReason));
        return draft;
    }

    private Map<String, Object> buildCandidateGraph(String userQuery, String candidateReason) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "candidate-worker");
        node.put("name", "候选Workflow执行");
        node.put("type", "WORKER");
        Map<String, Object> config = new HashMap<>();
        config.put("promptTemplate", "你将基于用户请求给出结构化执行结果，用户请求: ${userQuery}");
        config.put("agentKey", fallbackAgentKey);
        node.put("config", config);

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", Collections.singletonList(node));
        graph.put("edges", Collections.emptyList());
        graph.put("candidateReason", StringUtils.defaultIfBlank(candidateReason, "DEFINITION_NOT_MATCHED"));
        graph.put("sourceQuery", userQuery);
        return graph;
    }

    private RootWorkflowDraft normalizeAndValidateDraft(String userQuery, RootWorkflowDraft draft) {
        if (draft == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Root 规划未返回草案");
        }
        Map<String, Object> graph = deepCopyMap(draft.getGraphDefinition());
        List<Map<String, Object>> nodes = getMapList(graph, "nodes");
        if (nodes.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案节点为空");
        }
        List<Map<String, Object>> normalizedNodes = new ArrayList<>();
        Set<String> nodeIdSet = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || node.isEmpty()) {
                continue;
            }
            String nodeId = getString(node, "id", "nodeId", "node_id");
            if (StringUtils.isBlank(nodeId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案节点缺少id");
            }
            if (!nodeIdSet.add(nodeId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案节点id重复: " + nodeId);
            }
            String rawTaskType = StringUtils.defaultIfBlank(getString(node, "type", "taskType", "task_type"), "WORKER");
            TaskTypeEnum taskType;
            try {
                taskType = TaskTypeEnum.fromText(rawTaskType);
            } catch (Exception ex) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案节点类型非法: " + rawTaskType);
            }

            Map<String, Object> normalizedNode = new LinkedHashMap<>(node);
            normalizedNode.put("id", nodeId);
            normalizedNode.put("type", taskType.name());
            Map<String, Object> config = getMap(node, "config", "configSnapshot", "config_snapshot", "options");
            if (config == null) {
                config = new HashMap<>();
            } else {
                config = new HashMap<>(config);
            }
            normalizeNodeAgentConfig(nodeId, config);
            normalizedNode.put("config", config);
            normalizedNodes.add(normalizedNode);
        }
        if (normalizedNodes.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案没有可用节点");
        }

        List<Map<String, Object>> edges = getMapList(graph, "edges");
        List<Map<String, Object>> normalizedEdges = normalizeAndValidateEdges(edges, nodeIdSet);

        Map<String, Object> normalizedGraph = new HashMap<>(graph);
        normalizedGraph.put("nodes", normalizedNodes);
        normalizedGraph.put("edges", normalizedEdges);

        RootWorkflowDraft normalized = new RootWorkflowDraft();
        normalized.setCategory(StringUtils.defaultIfBlank(draft.getCategory(), "candidate"));
        normalized.setName(StringUtils.defaultIfBlank(draft.getName(), buildCandidateName(userQuery)));
        normalized.setRouteDescription(StringUtils.defaultIfBlank(draft.getRouteDescription(), userQuery));
        normalized.setGraphDefinition(normalizedGraph);
        normalized.setInputSchema(safeMutableMap(draft.getInputSchema()));
        normalized.setDefaultConfig(mergeSimpleMap(buildCandidateDefaultConfig(), draft.getDefaultConfig()));
        normalized.setToolPolicy(mergeSimpleMap(buildCandidateToolPolicy(), draft.getToolPolicy()));
        normalized.setConstraints(mergeSimpleMap(Collections.singletonMap("mode", "candidate-restricted"), draft.getConstraints()));
        normalized.setInputSchemaVersion(StringUtils.defaultIfBlank(draft.getInputSchemaVersion(), "v1"));
        normalized.setNodeSignature(StringUtils.defaultIfBlank(draft.getNodeSignature(), computeNodeSignature(normalizedGraph)));
        return normalized;
    }

    private void normalizeNodeAgentConfig(String nodeId, Map<String, Object> config) {
        Long configuredAgentId = getLong(config, "agentId", "agent_id");
        String configuredAgentKey = getString(config, "agentKey", "agent_key");
        if (configuredAgentId != null) {
            validateAgentId(configuredAgentId, nodeId);
            return;
        }
        if (StringUtils.isNotBlank(configuredAgentKey)) {
            if (isAgentKeyActive(configuredAgentKey)) {
                return;
            }
            String resolvedKey = resolveAvailableAgentKey(nodeId, configuredAgentKey);
            log.warn("候选草案节点agentKey不可用，已自动回退。nodeId={}, configuredAgentKey={}, resolvedAgentKey={}",
                    nodeId, configuredAgentKey, resolvedKey);
            config.remove("agentId");
            config.put("agentKey", resolvedKey);
            return;
        }
        String resolvedKey = resolveAvailableAgentKey(nodeId, null);
        config.remove("agentId");
        config.put("agentKey", resolvedKey);
    }

    private void validateAgentId(Long agentId, String nodeId) {
        if (agentRegistryRepository == null) {
            return;
        }
        AgentRegistryEntity agent = agentRegistryRepository.findById(agentId);
        if (agent == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "候选草案节点agentId不存在: nodeId=" + nodeId + ", agentId=" + agentId);
        }
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "候选草案节点agentId未激活: nodeId=" + nodeId + ", agentId=" + agentId);
        }
    }

    private boolean isAgentKeyActive(String agentKey) {
        if (StringUtils.isBlank(agentKey)) {
            return false;
        }
        if (agentRegistryRepository == null) {
            return true;
        }
        AgentRegistryEntity agent = agentRegistryRepository.findByKey(agentKey);
        return agent != null && Boolean.TRUE.equals(agent.getIsActive());
    }

    private String resolveAvailableAgentKey(String nodeId, String configuredAgentKey) {
        if (agentRegistryRepository == null) {
            return StringUtils.defaultIfBlank(fallbackAgentKey, rootAgentKey);
        }
        List<String> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(fallbackAgentKey)) {
            candidates.add(fallbackAgentKey);
        }
        if (StringUtils.isNotBlank(rootAgentKey) && !StringUtils.equals(rootAgentKey, fallbackAgentKey)) {
            candidates.add(rootAgentKey);
        }
        for (String candidate : candidates) {
            if (isAgentKeyActive(candidate)) {
                return candidate;
            }
        }
        String configured = StringUtils.defaultIfBlank(configuredAgentKey, "-");
        String fallbackCandidates = candidates.isEmpty() ? "-" : String.join(",", candidates);
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "候选草案节点无可用agentKey: nodeId=" + nodeId
                        + ", configuredAgentKey=" + configured
                        + ", fallbackCandidates=" + fallbackCandidates);
    }

    private List<Map<String, Object>> normalizeAndValidateEdges(List<Map<String, Object>> edges, Set<String> nodeIdSet) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String nodeId : nodeIdSet) {
            inDegree.put(nodeId, 0);
            adjacency.put(nodeId, new ArrayList<>());
        }

        for (Map<String, Object> edge : edges) {
            if (edge == null || edge.isEmpty()) {
                continue;
            }
            String from = getString(edge, "from", "source", "src");
            String to = getString(edge, "to", "target", "dst");
            if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
                continue;
            }
            if (isVirtualBoundaryEdge(from, to, nodeIdSet)) {
                log.debug("候选草案边界边已忽略: {} -> {}", from, to);
                continue;
            }
            if (!nodeIdSet.contains(from) || !nodeIdSet.contains(to)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "候选草案边引用了不存在节点: " + from + " -> " + to);
            }
            Map<String, Object> normalizedEdge = new LinkedHashMap<>();
            normalizedEdge.put("from", from);
            normalizedEdge.put("to", to);
            normalized.add(normalizedEdge);

            adjacency.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
            inDegree.put(to, inDegree.getOrDefault(to, 0) + 1);
        }

        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        int index = 0;
        int visited = 0;
        while (index < queue.size()) {
            String node = queue.get(index++);
            visited++;
            List<String> next = adjacency.getOrDefault(node, Collections.emptyList());
            for (String to : next) {
                int degree = inDegree.getOrDefault(to, 0) - 1;
                inDegree.put(to, degree);
                if (degree == 0) {
                    queue.add(to);
                }
            }
        }
        if (visited != nodeIdSet.size()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "候选草案图存在环路");
        }
        return normalized;
    }

    private boolean isVirtualBoundaryEdge(String from, String to, Set<String> nodeIdSet) {
        if (StringUtils.isBlank(from) || StringUtils.isBlank(to) || nodeIdSet == null || nodeIdSet.isEmpty()) {
            return false;
        }
        boolean fromIsNode = nodeIdSet.contains(from);
        boolean toIsNode = nodeIdSet.contains(to);

        if (!fromIsNode && !toIsNode) {
            return isVirtualEntryNodeId(from) || isVirtualExitNodeId(to);
        }
        if (!fromIsNode && toIsNode) {
            return isVirtualEntryNodeId(from);
        }
        if (fromIsNode && !toIsNode) {
            return isVirtualExitNodeId(to);
        }
        return false;
    }

    private boolean isVirtualEntryNodeId(String nodeId) {
        String normalized = normalizeBoundaryNodeId(nodeId);
        return normalized != null && VIRTUAL_ENTRY_NODE_IDS.contains(normalized);
    }

    private boolean isVirtualExitNodeId(String nodeId) {
        String normalized = normalizeBoundaryNodeId(nodeId);
        return normalized != null && VIRTUAL_EXIT_NODE_IDS.contains(normalized);
    }

    private String normalizeBoundaryNodeId(String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            return null;
        }
        return nodeId.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String buildDedupHash(String userQuery, RootWorkflowDraft draft) {
        String nodeSignature = StringUtils.defaultIfBlank(draft.getNodeSignature(), computeNodeSignature(draft.getGraphDefinition()));
        String payload = "intent=" + StringUtils.trimToEmpty(userQuery)
                + "|inputSchema=" + StringUtils.defaultIfBlank(draft.getInputSchemaVersion(), "v1")
                + "|constraints=" + stableJson(draft.getConstraints())
                + "|toolPolicy=" + stableJson(draft.getToolPolicy())
                + "|nodeSig=" + nodeSignature;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception ex) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Failed to build workflow dedup hash");
        }
    }

    private String computeNodeSignature(Map<String, Object> graphDefinition) {
        List<Map<String, Object>> nodes = new ArrayList<>(getMapList(graphDefinition, "nodes"));
        nodes.sort(Comparator.comparing(item -> StringUtils.defaultString(getString(item, "id"))));
        List<String> nodeSignatures = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = getString(node, "id", "nodeId", "node_id");
            String type = getString(node, "type", "taskType", "task_type");
            Map<String, Object> config = getMap(node, "config", "configSnapshot", "config_snapshot", "options");
            String agentKey = getString(config, "agentKey", "agent_key");
            Long agentId = getLong(config, "agentId", "agent_id");
            nodeSignatures.add(nodeId + ":" + StringUtils.defaultIfBlank(type, "WORKER") + ":"
                    + StringUtils.defaultIfBlank(agentKey, "-") + ":" + (agentId == null ? "-" : agentId));
        }

        List<Map<String, Object>> edges = new ArrayList<>(getMapList(graphDefinition, "edges"));
        edges.sort(Comparator.comparing(item -> StringUtils.defaultString(getString(item, "from"))
                + "->" + StringUtils.defaultString(getString(item, "to"))));
        List<String> edgeSignatures = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            edgeSignatures.add(StringUtils.defaultString(getString(edge, "from"))
                    + "->" + StringUtils.defaultString(getString(edge, "to")));
        }
        return String.join("|", nodeSignatures) + "#edges=" + String.join(",", edgeSignatures);
    }

    private String stableJson(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return "{}";
        }
        Object normalized = canonicalize(source);
        return jsonCodec.writeValue(normalized);
    }

    @SuppressWarnings("unchecked")
    private Object canonicalize(Object source) {
        if (source instanceof Map<?, ?>) {
            Map<String, Object> sorted = new TreeMap<>();
            Map<?, ?> map = (Map<?, ?>) source;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sorted.put(key, canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (source instanceof List<?>) {
            List<?> list = (List<?>) source;
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(canonicalize(item));
            }
            return normalized;
        }
        return source;
    }

    private void sleepBackoff(int attempt) {
        if (rootRetryBackoffMs <= 0L || attempt >= rootMaxAttempts) {
            return;
        }
        try {
            Thread.sleep(rootRetryBackoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
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

    private void enrichRequiredInputFromQuery(Map<String, Object> inputSchema,
                                              Map<String, Object> userInput,
                                              String userQuery) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }
        if (userInput == null) {
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
            if (hasInputValue(userInput.get(key))) {
                continue;
            }
            Object inferred = inferRequiredFieldValue(key, inputSchema, userQuery);
            if (!hasInputValue(inferred)) {
                continue;
            }
            userInput.put(key, inferred);
        }
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
            if (!hasInputValue(value)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Missing required input: " + key);
            }
        }
    }

    private boolean hasInputValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.isNotBlank((String) value);
        }
        if (value instanceof List<?>) {
            return !((List<?>) value).isEmpty();
        }
        if (value instanceof Map<?, ?>) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private Object inferRequiredFieldValue(String key,
                                           Map<String, Object> inputSchema,
                                           String userQuery) {
        if (StringUtils.isBlank(userQuery)) {
            return null;
        }
        String raw = extractFieldValueFromText(key, userQuery);
        if (StringUtils.isBlank(raw)) {
            raw = extractFieldValueFromDescription(key, inputSchema);
        }
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return convertBySchemaType(inputSchema, key, raw);
    }

    private String extractFieldValueFromText(String key, String userQuery) {
        List<String> aliases = buildFieldAliases(key);
        for (String alias : aliases) {
            String value = extractByAlias(alias, userQuery);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> buildFieldAliases(String key) {
        List<String> aliases = new ArrayList<>();
        aliases.add(key);

        String snake = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (!StringUtils.equalsIgnoreCase(snake, key)) {
            aliases.add(snake);
            aliases.add(snake.replace("_", " "));
        }

        String compact = key.toLowerCase(Locale.ROOT);
        if (!aliases.contains(compact)) {
            aliases.add(compact);
        }

        if ("productName".equalsIgnoreCase(key)) {
            aliases.add("商品名");
            aliases.add("商品名称");
            aliases.add("产品名");
            aliases.add("产品名称");
        }
        return aliases;
    }

    private String extractByAlias(String alias, String text) {
        if (StringUtils.isBlank(alias) || StringUtils.isBlank(text)) {
            return null;
        }
        String escapedAlias = Pattern.quote(alias);
        Pattern naturalPattern = Pattern.compile("(?:^|[\\s,，。;；])" + escapedAlias
                + "\\s*(?:是|为|=|:|：)\\s*[\"'“”]?([^,，。;；\\n\"'“”]+)");
        Matcher naturalMatcher = naturalPattern.matcher(text);
        if (naturalMatcher.find()) {
            return cleanCapturedValue(naturalMatcher.group(1));
        }

        Pattern strictKvPattern = Pattern.compile("[\"']?" + escapedAlias
                + "[\"']?\\s*[:=]\\s*[\"'“”]?([^,，。;；}\\]\\n\"'“”]+)");
        Matcher strictKvMatcher = strictKvPattern.matcher(text);
        if (strictKvMatcher.find()) {
            return cleanCapturedValue(strictKvMatcher.group(1));
        }
        return null;
    }

    private String cleanCapturedValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("^[\"'“”]+|[\"'“”]+$", "");
        return StringUtils.trimToNull(cleaned);
    }

    @SuppressWarnings("unchecked")
    private String extractFieldValueFromDescription(String key, Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty() || StringUtils.isBlank(key)) {
            return null;
        }
        Map<String, Object> properties = getMap(inputSchema, "properties");
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        Object fieldSchemaObj = properties.get(key);
        if (!(fieldSchemaObj instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaObj;
        String description = getString(fieldSchema, "description", "desc");
        if (StringUtils.isBlank(description)) {
            return null;
        }
        Matcher matcher = DESCRIPTION_FIXED_VALUE_PATTERN.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        return cleanCapturedValue(matcher.group(1));
    }

    @SuppressWarnings("unchecked")
    private Object convertBySchemaType(Map<String, Object> inputSchema, String key, String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        if (inputSchema == null || inputSchema.isEmpty()) {
            return rawValue;
        }
        Map<String, Object> properties = getMap(inputSchema, "properties");
        if (properties == null || properties.isEmpty()) {
            return rawValue;
        }
        Object fieldSchemaObj = properties.get(key);
        if (!(fieldSchemaObj instanceof Map<?, ?>)) {
            return rawValue;
        }
        Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaObj;
        String type = getString(fieldSchema, "type");
        if (StringUtils.isBlank(type)) {
            return rawValue;
        }
        String normalizedType = type.trim().toLowerCase(Locale.ROOT);
        try {
            if ("array".equals(normalizedType)) {
                String[] parts = rawValue.split("[,，/、;；]");
                List<String> values = new ArrayList<>();
                for (String part : parts) {
                    String value = StringUtils.trimToNull(part);
                    if (value != null) {
                        values.add(value);
                    }
                }
                return values;
            }
            if ("integer".equals(normalizedType)) {
                return Integer.parseInt(rawValue.trim());
            }
            if ("number".equals(normalizedType)) {
                return Double.parseDouble(rawValue.trim());
            }
            if ("boolean".equals(normalizedType)) {
                String lower = rawValue.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "y".equals(lower)
                        || "是".equals(rawValue.trim())) {
                    return true;
                }
                if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower) || "n".equals(lower)
                        || "否".equals(rawValue.trim())) {
                    return false;
                }
            }
        } catch (Exception ignore) {
            return rawValue;
        }
        return rawValue;
    }

    private Map<String, Object> buildGlobalContext(Long sessionId,
                                                   String userQuery,
                                                   RoutedWorkflow routedWorkflow,
                                                   Map<String, Object> userInput) {
        Map<String, Object> context = new HashMap<>();
        if (userInput != null) {
            context.putAll(userInput);
        }
        context.put("userQuery", userQuery);
        context.put("sessionId", sessionId);
        if (routedWorkflow != null) {
            context.put("routeType", routedWorkflow.decisionType == null ? null : routedWorkflow.decisionType.name());
            context.put("routeReason", routedWorkflow.reason);
            if (routedWorkflow.definition != null) {
                context.put("workflowDefinitionId", routedWorkflow.definition.getId());
                context.put("definitionKey", routedWorkflow.definition.getDefinitionKey());
                context.put("definitionVersion", routedWorkflow.definition.getVersion());
            }
            if (routedWorkflow.draft != null) {
                context.put("workflowDraftId", routedWorkflow.draft.getId());
                context.put("draftKey", routedWorkflow.draft.getDraftKey());
            }
        }
        return context;
    }

    private Integer resolvePriority(Map<String, Object> defaultConfig) {
        Integer priority = getInteger(defaultConfig, "priority");
        return priority != null ? priority : DEFAULT_PRIORITY;
    }

    private List<AgentTaskEntity> unfoldGraph(AgentPlanEntity plan,
                                              Map<String, Object> defaultConfig,
                                              Map<String, Object> executionGraph,
                                              Map<String, Object> globalContext) {
        List<Map<String, Object>> nodes = getMapList(executionGraph, "nodes");
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> edges = getMapList(executionGraph, "edges");
        Map<String, List<String>> dependencies = buildDependencies(edges);

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

    private Map<String, Object> mergeSimpleMap(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> merged = new HashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (override != null) {
            merged.putAll(override);
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

    private Long getLong(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
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

    private Map<String, Object> safeMutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
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

    private RoutingDecisionEntity buildRoutingDecisionEntity(Long sessionId,
                                                             Map<String, Object> extraContext,
                                                             RoutedWorkflow routedWorkflow) {
        RoutingDecisionEntity decision = new RoutingDecisionEntity();
        decision.setSessionId(sessionId);
        decision.setTurnId(extractTurnId(extraContext));
        decision.setDecisionType(routedWorkflow.decisionType);
        decision.setStrategy(routedWorkflow.strategy);
        decision.setScore(routedWorkflow.score);
        decision.setReason(routedWorkflow.reason);
        if (routedWorkflow.definition != null) {
            decision.setDefinitionId(routedWorkflow.definition.getId());
            decision.setDefinitionKey(routedWorkflow.definition.getDefinitionKey());
            decision.setDefinitionVersion(routedWorkflow.definition.getVersion());
        }
        if (routedWorkflow.draft != null) {
            decision.setDraftId(routedWorkflow.draft.getId());
            decision.setDraftKey(routedWorkflow.draft.getDraftKey());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", routedWorkflow.draft == null ? null : routedWorkflow.draft.getSourceType());
        decision.setMetadata(metadata);
        return decision;
    }

    private Long extractTurnId(Map<String, Object> extraContext) {
        if (extraContext == null || extraContext.isEmpty()) {
            return null;
        }
        Object value = extraContext.get("turnId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildDefinitionSnapshot(RoutedWorkflow routedWorkflow) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (routedWorkflow == null) {
            return snapshot;
        }
        snapshot.put("routeType", routedWorkflow.decisionType == null ? null : routedWorkflow.decisionType.name());
        snapshot.put("routeReason", routedWorkflow.reason);
        snapshot.put("routeStrategy", routedWorkflow.strategy);
        snapshot.put("routeScore", routedWorkflow.score);
        if (routedWorkflow.definition != null) {
            snapshot.put("definitionId", routedWorkflow.definition.getId());
            snapshot.put("definitionKey", routedWorkflow.definition.getDefinitionKey());
            snapshot.put("definitionVersion", routedWorkflow.definition.getVersion());
        }
        if (routedWorkflow.draft != null) {
            snapshot.put("draftId", routedWorkflow.draft.getId());
            snapshot.put("draftKey", routedWorkflow.draft.getDraftKey());
            snapshot.put("draftSourceType", routedWorkflow.draft.getSourceType());
        }
        snapshot.put("executionGraphHash", hashGraph(routedWorkflow.graphDefinition));
        return snapshot;
    }

    private String hashGraph(Map<String, Object> graphDefinition) {
        if (graphDefinition == null || graphDefinition.isEmpty()) {
            return null;
        }
        try {
            String canonical = jsonCodec.writeValue(new TreeMap<>(graphDefinition));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception ex) {
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static final class RoutedWorkflow {
        private RoutingDecisionTypeEnum decisionType;
        private String reason;
        private String strategy;
        private BigDecimal score;
        private WorkflowDefinitionEntity definition;
        private WorkflowDraftEntity draft;
        private Map<String, Object> graphDefinition;
        private Map<String, Object> inputSchema;
        private Map<String, Object> defaultConfig;
    }
}
