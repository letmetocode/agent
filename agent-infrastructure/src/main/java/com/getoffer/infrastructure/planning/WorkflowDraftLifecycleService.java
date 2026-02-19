package com.getoffer.infrastructure.planning;

import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import com.getoffer.domain.planning.service.WorkflowGraphPolicyKernel;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import com.getoffer.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Root Draft 生命周期服务：负责 root 规划、候选降级与 draft 落库复用。
 */
@Slf4j
public class WorkflowDraftLifecycleService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_PRIORITY = 0;
    private static final String DEFAULT_TENANT = "DEFAULT";
    private static final String DEFAULT_CREATOR = "SYSTEM";
    private static final int GRAPH_DSL_VERSION = WorkflowGraphPolicyKernel.GRAPH_DSL_VERSION;
    private static final String SOURCE_TYPE_AUTO_MISS_ROOT = PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_ROOT;
    private static final String SOURCE_TYPE_AUTO_MISS_FALLBACK = PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_FALLBACK;
    private static final String FALLBACK_REASON_ROOT_PLANNING_FAILED = PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNING_FAILED;
    private static final String FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT = PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT;
    private static final String FALLBACK_REASON_ROOT_PLANNER_DISABLED = PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNER_DISABLED;
    private static final String FALLBACK_REASON_ROOT_PLANNER_MISSING = PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNER_MISSING;

    private final IWorkflowDraftRepository workflowDraftRepository;
    private final IRootWorkflowDraftPlanner rootWorkflowDraftPlanner;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final boolean rootPlannerEnabled;
    private final String rootAgentKey;
    private final int rootMaxAttempts;
    private final long rootRetryBackoffMs;
    private final boolean fallbackSingleNodeEnabled;
    private final String fallbackAgentKey;
    private final long rootSoftTimeoutMs;
    private final PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService;
    private final JsonCodec jsonCodec;
    private final ExecutorService rootPlanningExecutor;

    public WorkflowDraftLifecycleService(IWorkflowDraftRepository workflowDraftRepository,
                                         IRootWorkflowDraftPlanner rootWorkflowDraftPlanner,
                                         IAgentRegistryRepository agentRegistryRepository,
                                         boolean rootPlannerEnabled,
                                         String rootAgentKey,
                                         int rootMaxAttempts,
                                         long rootRetryBackoffMs,
                                         boolean fallbackSingleNodeEnabled,
                                         String fallbackAgentKey,
                                         long rootSoftTimeoutMs,
                                         PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService,
                                         JsonCodec jsonCodec,
                                         ExecutorService rootPlanningExecutor) {
        this.workflowDraftRepository = workflowDraftRepository;
        this.rootWorkflowDraftPlanner = rootWorkflowDraftPlanner;
        this.agentRegistryRepository = agentRegistryRepository;
        this.rootPlannerEnabled = rootPlannerEnabled;
        this.rootAgentKey = StringUtils.defaultIfBlank(rootAgentKey, "root");
        this.rootMaxAttempts = Math.max(rootMaxAttempts, 1);
        this.rootRetryBackoffMs = Math.max(rootRetryBackoffMs, 0L);
        this.fallbackSingleNodeEnabled = fallbackSingleNodeEnabled;
        this.fallbackAgentKey = StringUtils.defaultIfBlank(fallbackAgentKey, this.rootAgentKey);
        this.rootSoftTimeoutMs = Math.max(rootSoftTimeoutMs, 0L);
        if (plannerFallbackPolicyDomainService == null) {
            throw new IllegalArgumentException("plannerFallbackPolicyDomainService must not be null");
        }
        this.plannerFallbackPolicyDomainService = plannerFallbackPolicyDomainService;
        this.jsonCodec = jsonCodec;
        this.rootPlanningExecutor = rootPlanningExecutor;
    }

    public WorkflowDraftEntity loadOrCreateDraft(Long sessionId,
                                                 String userQuery,
                                                 Map<String, Object> extraContext) {
        if (!rootPlannerEnabled) {
            log.info("Root planner is disabled. use fallback single-node candidate. sessionId={}", sessionId);
            return persistOrReuseDraft(userQuery,
                    buildFallbackSingleNodeDraft(userQuery, FALLBACK_REASON_ROOT_PLANNER_DISABLED, 0),
                    SOURCE_TYPE_AUTO_MISS_FALLBACK);
        }
        if (rootWorkflowDraftPlanner == null) {
            log.warn("Root planner bean not available. use fallback single-node candidate. sessionId={}", sessionId);
            return persistOrReuseDraft(userQuery,
                    buildFallbackSingleNodeDraft(userQuery, FALLBACK_REASON_ROOT_PLANNER_MISSING, 0),
                    SOURCE_TYPE_AUTO_MISS_FALLBACK);
        }

        Exception lastError = null;
        int attemptsUsed = 0;
        String fallbackReason = FALLBACK_REASON_ROOT_PLANNING_FAILED;
        for (int attempt = 1; attempt <= rootMaxAttempts; attempt++) {
            attemptsUsed = attempt;
            try {
                Map<String, Object> planningContext = buildRootPlanningContext(sessionId, userQuery, extraContext, attempt);
                RootWorkflowDraft draft = planDraftWithSoftTimeout(sessionId, userQuery, planningContext);
                return persistOrReuseDraft(userQuery, draft, SOURCE_TYPE_AUTO_MISS_ROOT);
            } catch (Exception ex) {
                lastError = ex;
                boolean nonRetryable = isNonRetryableRootPlanningError(ex);
                boolean softTimeout = isRootPlannerSoftTimeout(ex);
                PlannerFallbackPolicyDomainService.RootPlanningFailureDecision failureDecision =
                        plannerFallbackPolicyDomainService.decideOnRootPlanningFailure(
                                attempt,
                                rootMaxAttempts,
                                nonRetryable,
                                softTimeout
                        );
                fallbackReason = failureDecision.fallbackReason();
                log.warn("Root candidate planning failed. sessionId={}, attempt={}/{}, nonRetryable={}, softTimeout={}, reason={}",
                        sessionId, attempt, rootMaxAttempts, nonRetryable, softTimeout, ex.getMessage());
                if (!failureDecision.shouldRetry()) {
                    log.warn("Root candidate planning stops retrying. sessionId={}, attempt={}, maxAttempts={}, nonRetryable={}, softTimeout={}",
                            sessionId, attempt, rootMaxAttempts, nonRetryable, softTimeout);
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        if (!fallbackSingleNodeEnabled) {
            String reason = lastError == null ? "unknown" : StringUtils.defaultIfBlank(lastError.getMessage(), "unknown");
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "Root规划失败且已重试" + attemptsUsed + "次: " + reason);
        }
        log.warn("Root candidate planning exhausted retries. fallback to single-node candidate. sessionId={}, attemptsUsed={}, fallbackReason={}",
                sessionId, attemptsUsed, fallbackReason);
        return persistOrReuseDraft(userQuery,
                buildFallbackSingleNodeDraft(userQuery, fallbackReason, attemptsUsed),
                SOURCE_TYPE_AUTO_MISS_FALLBACK);
    }

    public Map<String, Object> normalizeAndValidateGraphDefinition(Map<String, Object> rawGraph,
                                                                   String scene,
                                                                   boolean allowCandidateVersionUpgrade) {
        try {
            return WorkflowGraphPolicyKernel.normalizeAndValidateGraphDefinition(
                    rawGraph,
                    scene,
                    allowCandidateVersionUpgrade,
                    this::normalizeNodeAgentConfig
            );
        } catch (IllegalArgumentException ex) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ex.getMessage());
        }
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
        context.put("softTimeoutMs", rootSoftTimeoutMs);
        if (extraContext != null && !extraContext.isEmpty()) {
            context.put("extraContext", extraContext);
        }
        return context;
    }

    private boolean isNonRetryableRootPlanningError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof NonRetryableRootPlanningException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private RootWorkflowDraft planDraftWithSoftTimeout(Long sessionId,
                                                       String userQuery,
                                                       Map<String, Object> planningContext) throws Exception {
        if (rootSoftTimeoutMs <= 0L) {
            return rootWorkflowDraftPlanner.planDraft(sessionId, userQuery, planningContext);
        }

        Future<RootWorkflowDraft> future = rootPlanningExecutor.submit(
                () -> rootWorkflowDraftPlanner.planDraft(sessionId, userQuery, planningContext)
        );
        try {
            return future.get(rootSoftTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new NonRetryableRootPlanningException(
                    "Root planning soft timeout exceeded " + rootSoftTimeoutMs + "ms", ex);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new NonRetryableRootPlanningException("Root planning interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private boolean isRootPlannerSoftTimeout(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof TimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
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
                WorkflowGraphPolicyKernel.computeNodeSignature(draftEntity.getGraphDefinition())));
        draftEntity.setDedupHash(dedupHash);
        draftEntity.setSourceType(sourceType);
        draftEntity.setStatus(WorkflowDraftStatusEnum.DRAFT);
        draftEntity.setCreatedBy(DEFAULT_CREATOR);
        return workflowDraftRepository.save(draftEntity);
    }

    private String buildCandidateName(String userQuery) {
        String normalized = StringUtils.defaultIfBlank(userQuery, "candidate").trim();
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

    private RootWorkflowDraft buildFallbackSingleNodeDraft(String userQuery,
                                                           String candidateReason,
                                                           int plannerAttempts) {
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
        constraints.put("rootPlanningAttempts", Math.max(plannerAttempts, 0));
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
        node.put("joinPolicy", WorkflowGraphPolicyKernel.JOIN_POLICY_ALL);
        node.put("failurePolicy", WorkflowGraphPolicyKernel.FAILURE_POLICY_FAIL_SAFE);
        Map<String, Object> config = new HashMap<>();
        config.put("promptTemplate", "你将基于用户请求给出结构化执行结果，用户请求: ${userQuery}");
        config.put("agentKey", fallbackAgentKey);
        node.put("config", config);

        Map<String, Object> graph = new HashMap<>();
        graph.put("version", GRAPH_DSL_VERSION);
        graph.put("nodes", Collections.singletonList(node));
        graph.put("groups", Collections.emptyList());
        graph.put("edges", Collections.emptyList());
        graph.put("candidateReason", StringUtils.defaultIfBlank(candidateReason, "DEFINITION_NOT_MATCHED"));
        graph.put("sourceQuery", userQuery);
        return graph;
    }

    private RootWorkflowDraft normalizeAndValidateDraft(String userQuery, RootWorkflowDraft draft) {
        if (draft == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Root 规划未返回草案");
        }

        Map<String, Object> normalizedGraph;
        try {
            normalizedGraph = normalizeAndValidateGraphDefinition(
                    deepCopyMap(draft.getGraphDefinition()),
                    "候选草案",
                    true
            );
        } catch (AppException ex) {
            throw new NonRetryableRootPlanningException("候选草案结构非法: " + ex.getMessage(), ex);
        }

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
        normalized.setNodeSignature(StringUtils.defaultIfBlank(draft.getNodeSignature(),
                WorkflowGraphPolicyKernel.computeNodeSignature(normalizedGraph)));
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
        List<String> candidates = plannerFallbackPolicyDomainService.buildRootFallbackAgentCandidates(
                fallbackAgentKey,
                rootAgentKey
        );
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

    private String buildDedupHash(String userQuery, RootWorkflowDraft draft) {
        String nodeSignature = StringUtils.defaultIfBlank(draft.getNodeSignature(),
                WorkflowGraphPolicyKernel.computeNodeSignature(draft.getGraphDefinition()));
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

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
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

    private static final class NonRetryableRootPlanningException extends RuntimeException {
        private NonRetryableRootPlanningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
