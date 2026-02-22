package com.getoffer.trigger.http;

import com.getoffer.api.dto.SessionDetailDTO;
import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository;
import com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.entity.QualityEvaluationEventEntity;
import com.getoffer.domain.task.model.valobj.QualityExperimentSummary;
import com.getoffer.domain.task.adapter.repository.IQualityEvaluationEventRepository;
import com.getoffer.trigger.application.common.TaskDetailViewAssembler;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 控制台分页查询与知识库详情 API。
 */
@RestController
@RequestMapping("/api")
public class ConsoleQueryController {

    private final IAgentSessionRepository agentSessionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final IPlanTaskEventRepository planTaskEventRepository;
    private final IQualityEvaluationEventRepository qualityEvaluationEventRepository;
    private final IVectorStoreRegistryRepository vectorStoreRegistryRepository;
    private final TaskDetailViewAssembler taskDetailViewAssembler;

    public ConsoleQueryController(IAgentSessionRepository agentSessionRepository,
                                  IAgentPlanRepository agentPlanRepository,
                                  IAgentTaskRepository agentTaskRepository,
                                  IPlanTaskEventRepository planTaskEventRepository,
                                  IQualityEvaluationEventRepository qualityEvaluationEventRepository,
                                  IVectorStoreRegistryRepository vectorStoreRegistryRepository,
                                  TaskDetailViewAssembler taskDetailViewAssembler) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventRepository = planTaskEventRepository;
        this.qualityEvaluationEventRepository = qualityEvaluationEventRepository;
        this.vectorStoreRegistryRepository = vectorStoreRegistryRepository;
        this.taskDetailViewAssembler = taskDetailViewAssembler;
    }

    @GetMapping("/sessions/list")
    public Response<Map<String, Object>> listSessions(@RequestParam("userId") String userId,
                                                      @RequestParam(value = "activeOnly", required = false) Boolean activeOnly,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "page", required = false) Integer page,
                                                      @RequestParam(value = "size", required = false) Integer size) {
        if (StringUtils.isBlank(userId)) {
            return illegal("userId不能为空");
        }

        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        String normalizedKeyword = normalizeKeyword(keyword);
        int offset = (normalizedPage - 1) * normalizedSize;

        long totalCount = agentSessionRepository.countByUserIdAndFilters(userId, activeOnly, normalizedKeyword);
        if (totalCount <= 0) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }
        List<AgentSessionEntity> sessions = agentSessionRepository.findByUserIdAndFiltersPaged(
                userId,
                activeOnly,
                normalizedKeyword,
                offset,
                normalizedSize
        );
        List<SessionDetailDTO> items = (sessions == null ? Collections.<AgentSessionEntity>emptyList() : sessions).stream()
                .map(this::toSessionDetailDTO)
                .collect(Collectors.toList());
        return success(pagedResult(normalizedPage, normalizedSize, toSafeTotal(totalCount), items));
    }

    @GetMapping("/tasks/paged")
    public Response<Map<String, Object>> listTasksPaged(
            @RequestParam(value = "status", required = false) String statusText,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        int offset = (normalizedPage - 1) * normalizedSize;
        TaskStatusEnum status = parseTaskStatus(statusText);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<Long> scopedPlanIds = null;

        if (sessionId != null) {
            List<AgentPlanEntity> sessionPlans = agentPlanRepository.findBySessionId(sessionId);
            if (sessionPlans == null || sessionPlans.isEmpty()) {
                return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
            }
            scopedPlanIds = sessionPlans.stream()
                    .map(AgentPlanEntity::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (scopedPlanIds.isEmpty()) {
                return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
            }
        }

        long totalCount = agentTaskRepository.countByFilters(status, normalizedKeyword, planId, scopedPlanIds);
        if (totalCount <= 0) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        List<AgentTaskEntity> pagedTasks = agentTaskRepository.findByFiltersPaged(
                status,
                normalizedKeyword,
                planId,
                scopedPlanIds,
                offset,
                normalizedSize
        );
        List<AgentTaskEntity> taskItems = pagedTasks == null ? Collections.emptyList() : pagedTasks;
        Map<Long, Long> latestExecutionTimeMap = taskDetailViewAssembler.resolveLatestExecutionTimeMap(taskItems);
        List<TaskDetailDTO> items = taskItems.stream()
                .map(task -> taskDetailViewAssembler.toTaskDetailDTO(task, latestExecutionTimeMap))
                .collect(Collectors.toList());

        return success(pagedResult(normalizedPage, normalizedSize, toSafeTotal(totalCount), items));
    }

    @GetMapping("/logs/paged")
    public Response<Map<String, Object>> listLogsPaged(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        int offset = (normalizedPage - 1) * normalizedSize;

        String normalizedLevel = normalizeKeyword(level).toUpperCase(Locale.ROOT);
        if (!normalizedLevel.isEmpty() && !isSupportedLogLevel(normalizedLevel)) {
            return illegal("日志级别非法，仅支持 INFO/WARN/ERROR");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedTraceId = normalizeKeyword(traceId);

        List<Long> targetPlanIds = resolveTargetPlanIds(planId);
        if (targetPlanIds == null) {
            return illegal("计划不存在");
        }
        if (targetPlanIds.isEmpty()) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        long totalCount = planTaskEventRepository.countLogs(
                targetPlanIds,
                taskId,
                normalizedLevel,
                normalizedTraceId,
                normalizedKeyword
        );
        if (totalCount <= 0) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        List<PlanTaskEventEntity> events = planTaskEventRepository.findLogsPaged(
                targetPlanIds,
                taskId,
                normalizedLevel,
                normalizedTraceId,
                normalizedKeyword,
                offset,
                normalizedSize
        );

        List<Map<String, Object>> items = events == null ? Collections.emptyList() : events.stream()
                .map(this::toLogItem)
                .collect(Collectors.toList());
        int safeTotal = totalCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCount;
        return success(pagedResult(normalizedPage, normalizedSize, safeTotal, items));
    }

    @GetMapping("/logs/tool-policy/paged")
    public Response<Map<String, Object>> listToolPolicyLogsPaged(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "policyAction", required = false) String policyAction,
            @RequestParam(value = "policyMode", required = false) String policyMode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        int offset = (normalizedPage - 1) * normalizedSize;

        List<Long> targetPlanIds = resolveTargetPlanIds(planId);
        if (targetPlanIds == null) {
            return illegal("计划不存在");
        }
        if (targetPlanIds.isEmpty()) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        String normalizedPolicyAction = normalizeKeyword(policyAction);
        String normalizedPolicyMode = normalizeKeyword(policyMode);
        String normalizedKeyword = normalizeKeyword(keyword);
        long totalCount = planTaskEventRepository.countToolPolicyLogs(
                targetPlanIds,
                taskId,
                normalizedPolicyAction,
                normalizedPolicyMode,
                normalizedKeyword
        );
        if (totalCount <= 0) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }
        List<PlanTaskEventEntity> events = planTaskEventRepository.findToolPolicyLogsPaged(
                targetPlanIds,
                taskId,
                normalizedPolicyAction,
                normalizedPolicyMode,
                normalizedKeyword,
                offset,
                normalizedSize
        );
        List<Map<String, Object>> items = events == null ? Collections.emptyList() : events.stream()
                .map(this::toToolPolicyLogItem)
                .collect(Collectors.toList());
        return success(pagedResult(normalizedPage, normalizedSize, toSafeTotal(totalCount), items));
    }

    @GetMapping("/quality/evaluations/paged")
    public Response<Map<String, Object>> listQualityEvaluationsPaged(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "experimentKey", required = false) String experimentKey,
            @RequestParam(value = "experimentVariant", required = false) String experimentVariant,
            @RequestParam(value = "evaluatorType", required = false) String evaluatorType,
            @RequestParam(value = "pass", required = false) Boolean pass,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        int offset = (normalizedPage - 1) * normalizedSize;

        String normalizedExperimentKey = normalizeKeyword(experimentKey);
        String normalizedExperimentVariant = normalizeKeyword(experimentVariant);
        String normalizedEvaluatorType = normalizeKeyword(evaluatorType);
        String normalizedKeyword = normalizeKeyword(keyword);
        long totalCount = qualityEvaluationEventRepository.countByFilters(
                planId,
                taskId,
                normalizedExperimentKey,
                normalizedExperimentVariant,
                normalizedEvaluatorType,
                pass,
                normalizedKeyword
        );
        if (totalCount <= 0) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        List<QualityEvaluationEventEntity> events = qualityEvaluationEventRepository.findByFiltersPaged(
                planId,
                taskId,
                normalizedExperimentKey,
                normalizedExperimentVariant,
                normalizedEvaluatorType,
                pass,
                normalizedKeyword,
                offset,
                normalizedSize
        );
        List<Map<String, Object>> items = events == null ? Collections.emptyList() : events.stream()
                .map(this::toQualityEvaluationItem)
                .collect(Collectors.toList());
        return success(pagedResult(normalizedPage, normalizedSize, toSafeTotal(totalCount), items));
    }

    @GetMapping("/quality/evaluations/experiments/summary")
    public Response<List<Map<String, Object>>> summarizeQualityExperiments(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "experimentKey", required = false) String experimentKey,
            @RequestParam(value = "evaluatorType", required = false) String evaluatorType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        int normalizedLimit = limit == null ? 20 : Math.max(1, Math.min(200, limit));
        List<QualityExperimentSummary> summaries = qualityEvaluationEventRepository.summarizeByExperiment(
                planId,
                normalizeKeyword(experimentKey),
                normalizeKeyword(evaluatorType),
                normalizedLimit
        );
        List<Map<String, Object>> data = summaries == null ? Collections.emptyList() : summaries.stream()
                .map(this::toQualitySummaryItem)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/knowledge-bases/{id}")
    public Response<Map<String, Object>> getKnowledgeBase(@PathVariable("id") Long kbId) {
        if (kbId == null) {
            return illegal("知识库ID不能为空");
        }
        VectorStoreRegistryEntity entity = vectorStoreRegistryRepository.findById(kbId);
        if (entity == null) {
            return illegal("知识库不存在");
        }

        Map<String, Object> connectionConfig = entity.getConnectionConfig() == null ? Collections.emptyMap() : entity.getConnectionConfig();
        long documentCount = parseLong(connectionConfig.get("documentCount"), parseLong(connectionConfig.get("docCount"), 0L));
        long chunkCount = parseLong(connectionConfig.get("chunkCount"), 0L);

        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId());
        result.put("name", entity.getName());
        result.put("storeType", entity.getStoreType());
        result.put("collectionName", entity.getCollectionName());
        result.put("dimension", entity.getDimension());
        result.put("isActive", entity.getIsActive());
        result.put("connectionConfig", connectionConfig);
        result.put("documentCount", documentCount);
        result.put("chunkCount", chunkCount);
        result.put("updatedAt", entity.getUpdatedAt());
        return success(result);
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public Response<List<Map<String, Object>>> listKnowledgeDocuments(@PathVariable("id") Long kbId) {
        if (kbId == null) {
            return illegal("知识库ID不能为空");
        }
        VectorStoreRegistryEntity entity = vectorStoreRegistryRepository.findById(kbId);
        if (entity == null) {
            return illegal("知识库不存在");
        }

        Map<String, Object> connectionConfig = entity.getConnectionConfig() == null ? Collections.emptyMap() : entity.getConnectionConfig();
        Object docsObj = connectionConfig.get("documents");
        List<Map<String, Object>> items = new ArrayList<>();

        if (docsObj instanceof List<?> docs) {
            int index = 1;
            for (Object item : docs) {
                if (!(item instanceof Map<?, ?> mapItem)) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("id", parseLong(mapItem.get("id"), (long) index));
                Object nameValue = mapItem.get("name");
                row.put("name", String.valueOf(nameValue == null ? "document-" + index : nameValue));
                row.put("chunks", parseLong(mapItem.get("chunks"), 0L));
                Object statusValue = mapItem.get("status");
                row.put("status", String.valueOf(statusValue == null ? (Boolean.TRUE.equals(entity.getIsActive()) ? "INDEXED" : "DISABLED") : statusValue));
                Object updatedAtValue = mapItem.get("updatedAt");
                row.put("updatedAt", updatedAtValue == null ? entity.getUpdatedAt() : updatedAtValue);
                items.add(row);
                index++;
            }
        }

        if (items.isEmpty()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 1L);
            row.put("name", entity.getCollectionName() == null ? entity.getName() : entity.getCollectionName());
            row.put("chunks", parseLong(connectionConfig.get("chunkCount"), 0L));
            row.put("status", Boolean.TRUE.equals(entity.getIsActive()) ? "INDEXED" : "DISABLED");
            row.put("updatedAt", entity.getUpdatedAt());
            items.add(row);
        }

        return success(items);
    }

    private Map<String, Object> toLogItem(PlanTaskEventEntity event) {
        Map<String, Object> item = new HashMap<>();
        String eventName = event.getEventType() == null ? null : event.getEventType().getEventName();
        Map<String, Object> eventData = event.getEventData() == null ? Collections.emptyMap() : event.getEventData();
        item.put("id", event.getId());
        item.put("planId", event.getPlanId());
        item.put("taskId", event.getTaskId());
        item.put("eventType", event.getEventType());
        item.put("eventName", eventName);
        item.put("eventData", eventData);
        item.put("level", resolveEventLevel(event).name());
        item.put("traceId", eventData.getOrDefault("traceId", "-"));
        item.put("createdAt", event.getCreatedAt());
        return item;
    }

    private Map<String, Object> toToolPolicyLogItem(PlanTaskEventEntity event) {
        Map<String, Object> item = toLogItem(event);
        Map<String, Object> eventData = event == null || event.getEventData() == null
                ? Collections.emptyMap()
                : event.getEventData();
        item.put("auditCategory", eventData.getOrDefault("auditCategory", ""));
        item.put("policyAction", eventData.getOrDefault("policyAction", ""));
        item.put("policyMode", eventData.getOrDefault("policyMode", ""));
        item.put("allowHit", Boolean.TRUE.equals(eventData.get("allowHit")));
        item.put("blockHit", Boolean.TRUE.equals(eventData.get("blockHit")));
        item.put("allowedTools", toStringList(eventData.get("allowedTools")));
        item.put("blockedTools", toStringList(eventData.get("blockedTools")));
        item.put("selectedAgentId", eventData.get("selectedAgentId"));
        item.put("selectedAgentKey", eventData.get("selectedAgentKey"));
        item.put("selectionSource", eventData.get("selectionSource"));
        return item;
    }

    private Map<String, Object> toQualityEvaluationItem(QualityEvaluationEventEntity event) {
        Map<String, Object> item = new HashMap<>();
        Map<String, Object> payload = event == null || event.getPayload() == null
                ? Collections.emptyMap()
                : event.getPayload();
        item.put("id", event == null ? null : event.getId());
        item.put("planId", event == null ? null : event.getPlanId());
        item.put("taskId", event == null ? null : event.getTaskId());
        item.put("executionId", event == null ? null : event.getExecutionId());
        item.put("evaluatorType", event == null ? null : event.getEvaluatorType());
        item.put("experimentKey", event == null ? null : event.getExperimentKey());
        item.put("experimentVariant", event == null ? null : event.getExperimentVariant());
        item.put("schemaVersion", event == null ? null : event.getSchemaVersion());
        item.put("score", event == null ? null : event.getScore());
        item.put("pass", event != null && Boolean.TRUE.equals(event.getPass()));
        item.put("feedback", event == null ? null : event.getFeedback());
        item.put("payload", payload);
        item.put("bucket", payload.get("bucket"));
        item.put("rolloutPercent", payload.get("rolloutPercent"));
        item.put("createdAt", event == null ? null : event.getCreatedAt());
        return item;
    }

    private Map<String, Object> toQualitySummaryItem(QualityExperimentSummary summary) {
        Map<String, Object> item = new HashMap<>();
        long total = summary == null || summary.getTotalCount() == null ? 0L : summary.getTotalCount();
        long passCount = summary == null || summary.getPassCount() == null ? 0L : summary.getPassCount();
        long failCount = Math.max(0L, total - passCount);
        double passRate = total <= 0 ? 0D : passCount * 1.0D / total;
        item.put("experimentKey", summary == null ? null : summary.getExperimentKey());
        item.put("experimentVariant", summary == null ? null : summary.getExperimentVariant());
        item.put("totalCount", total);
        item.put("passCount", passCount);
        item.put("failCount", failCount);
        item.put("passRate", passRate);
        item.put("avgScore", summary == null ? null : summary.getAvgScore());
        item.put("lastEvaluatedAt", summary == null ? null : summary.getLastEvaluatedAt());
        return item;
    }

    private List<Long> resolveTargetPlanIds(Long planId) {
        if (planId != null) {
            AgentPlanEntity targetPlan = agentPlanRepository.findById(planId);
            if (targetPlan == null) {
                return null;
            }
            return Collections.singletonList(targetPlan.getId());
        }
        List<AgentPlanEntity> recentPlans = agentPlanRepository.findRecent(100);
        return (recentPlans == null ? Collections.<AgentPlanEntity>emptyList() : recentPlans).stream()
                .map(AgentPlanEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .collect(Collectors.toList());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                return Collections.singletonList(trimmed);
            }
        }
        return Collections.emptyList();
    }

    private EventLevel resolveEventLevel(PlanTaskEventEntity event) {
        if (event == null || event.getEventType() == null) {
            return EventLevel.INFO;
        }
        if (event.getEventType() == PlanTaskEventTypeEnum.TASK_LOG) {
            return EventLevel.WARN;
        }
        if (event.getEventType() == PlanTaskEventTypeEnum.PLAN_FINISHED) {
            return EventLevel.ERROR;
        }
        if (event.getEventType() == PlanTaskEventTypeEnum.TASK_COMPLETED) {
            String status = event.getEventData() == null ? "" : String.valueOf(event.getEventData().getOrDefault("status", "")).toUpperCase(Locale.ROOT);
            return "FAILED".equals(status) ? EventLevel.ERROR : EventLevel.INFO;
        }
        return EventLevel.INFO;
    }

    private TaskStatusEnum parseTaskStatus(String statusText) {
        String normalized = normalizeKeyword(statusText).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        for (TaskStatusEnum item : TaskStatusEnum.values()) {
            if (item.name().equals(normalized) || item.getCode().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        return null;
    }

    private String normalizeKeyword(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedLogLevel(String level) {
        return "INFO".equals(level) || "WARN".equals(level) || "ERROR".equals(level);
    }

    private Map<String, Object> pagedResult(int page, int size, int total, List<?> items) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("totalPages", total == 0 ? 0 : (int) Math.ceil(total / (double) size));
        return result;
    }

    private int toSafeTotal(long total) {
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private SessionDetailDTO toSessionDetailDTO(AgentSessionEntity session) {
        SessionDetailDTO dto = new SessionDetailDTO();
        dto.setSessionId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setTitle(session.getTitle());
        dto.setAgentKey(session.getAgentKey());
        dto.setScenario(session.getScenario());
        dto.setActive(session.getIsActive());
        dto.setMetaInfo(session.getMetaInfo());
        dto.setCreatedAt(session.getCreatedAt());
        return dto;
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private enum EventLevel {
        INFO,
        WARN,
        ERROR
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
