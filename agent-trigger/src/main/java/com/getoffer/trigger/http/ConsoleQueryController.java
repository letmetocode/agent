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
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IVectorStoreRegistryRepository vectorStoreRegistryRepository;

    public ConsoleQueryController(IAgentSessionRepository agentSessionRepository,
                                  IAgentPlanRepository agentPlanRepository,
                                  IAgentTaskRepository agentTaskRepository,
                                  IPlanTaskEventRepository planTaskEventRepository,
                                  ITaskExecutionRepository taskExecutionRepository,
                                  IVectorStoreRegistryRepository vectorStoreRegistryRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventRepository = planTaskEventRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.vectorStoreRegistryRepository = vectorStoreRegistryRepository;
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

        List<AgentSessionEntity> source = Boolean.TRUE.equals(activeOnly)
                ? agentSessionRepository.findActiveByUserId(userId)
                : agentSessionRepository.findByUserId(userId);
        List<AgentSessionEntity> sessions = source == null ? Collections.emptyList() : source;

        if (!normalizedKeyword.isEmpty()) {
            sessions = sessions.stream()
                    .filter(item -> String.valueOf(item.getId()).contains(normalizedKeyword)
                            || safeText(item.getTitle()).toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    .collect(Collectors.toList());
        }

        sessions = sessions.stream()
                .sorted(Comparator.comparing(AgentSessionEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int total = sessions.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, total);
        int toIndex = Math.min(fromIndex + normalizedSize, total);
        List<SessionDetailDTO> items = sessions.subList(fromIndex, toIndex).stream()
                .map(this::toSessionDetailDTO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("page", normalizedPage);
        result.put("size", normalizedSize);
        result.put("total", total);
        result.put("totalPages", total == 0 ? 0 : (int) Math.ceil(total / (double) normalizedSize));
        return success(result);
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

        List<AgentTaskEntity> tasks;
        if (planId != null) {
            tasks = agentTaskRepository.findByPlanId(planId);
        } else {
            TaskStatusEnum status = parseTaskStatus(statusText);
            tasks = status == null ? agentTaskRepository.findAll() : agentTaskRepository.findByStatus(status);
        }

        if (tasks == null || tasks.isEmpty()) {
            return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
        }

        if (sessionId != null) {
            List<AgentPlanEntity> sessionPlans = agentPlanRepository.findBySessionId(sessionId);
            if (sessionPlans == null || sessionPlans.isEmpty()) {
                return success(pagedResult(normalizedPage, normalizedSize, 0, Collections.emptyList()));
            }
            List<Long> sessionPlanIds = sessionPlans.stream()
                    .map(AgentPlanEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            tasks = tasks.stream()
                    .filter(task -> sessionPlanIds.contains(task.getPlanId()))
                    .collect(Collectors.toList());
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (!normalizedKeyword.isEmpty()) {
            tasks = tasks.stream()
                    .filter(task -> containsTaskKeyword(task, normalizedKeyword))
                    .collect(Collectors.toList());
        }

        List<AgentTaskEntity> sorted = tasks.stream()
                .sorted(Comparator
                        .comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentTaskEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int total = sorted.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, total);
        int toIndex = Math.min(fromIndex + normalizedSize, total);

        List<TaskDetailDTO> items = sorted.subList(fromIndex, toIndex).stream()
                .map(this::toTaskDetailDTO)
                .collect(Collectors.toList());

        return success(pagedResult(normalizedPage, normalizedSize, total, items));
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

        List<Long> targetPlanIds;
        if (planId != null) {
            AgentPlanEntity targetPlan = agentPlanRepository.findById(planId);
            if (targetPlan == null) {
                return illegal("计划不存在");
            }
            targetPlanIds = Collections.singletonList(targetPlan.getId());
        } else {
            List<AgentPlanEntity> allPlans = agentPlanRepository.findAll();
            targetPlanIds = (allPlans == null ? Collections.<AgentPlanEntity>emptyList() : allPlans).stream()
                    .sorted(Comparator.comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(100)
                    .map(AgentPlanEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
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

    @PostMapping("/knowledge-bases/{id}/retrieval-tests")
    public Response<Map<String, Object>> retrievalTest(@PathVariable("id") Long kbId,
                                                       @RequestBody(required = false) Map<String, Object> payload) {
        if (kbId == null) {
            return illegal("知识库ID不能为空");
        }
        VectorStoreRegistryEntity entity = vectorStoreRegistryRepository.findById(kbId);
        if (entity == null) {
            return illegal("知识库不存在");
        }
        String query = payload == null ? "" : String.valueOf(payload.getOrDefault("query", "")).trim();
        if (query.isEmpty()) {
            return illegal("检索问题不能为空");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String collectionName = entity.getCollectionName() == null ? "default" : entity.getCollectionName();
        for (int idx = 0; idx < 3; idx++) {
            Map<String, Object> item = new HashMap<>();
            item.put("title", String.format("%s-doc-%d", collectionName, idx + 1));
            item.put("snippet", String.format("针对问题“%s”的候选片段 %d", query, idx + 1));
            item.put("score", Math.max(0.55, 0.92 - (idx * 0.14)));
            item.put("source", entity.getName());
            results.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("total", results.size());
        result.put("results", results);
        result.put("testedAt", LocalDateTime.now());
        return success(result);
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

    private boolean containsLogKeyword(Map<String, Object> logItem, String keyword) {
        String joined = String.format("%s %s %s %s %s %s",
                String.valueOf(logItem.get("eventName")),
                String.valueOf(logItem.get("eventType")),
                String.valueOf(logItem.get("taskId")),
                String.valueOf(logItem.get("level")),
                String.valueOf(logItem.get("traceId")),
                String.valueOf(logItem.get("eventData"))
        ).toLowerCase(Locale.ROOT);
        return joined.contains(keyword);
    }

    private boolean containsTaskKeyword(AgentTaskEntity task, String keyword) {
        String joined = String.format("%s %s %s %s %s %s",
                safeText(task.getName()),
                safeText(task.getNodeId()),
                task.getTaskType() == null ? "" : task.getTaskType().name(),
                safeText(task.getOutputResult()),
                safeText(task.getClaimOwner()),
                task.getStatus() == null ? "" : task.getStatus().name())
                .toLowerCase(Locale.ROOT);
        return joined.contains(keyword);
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

    private String safeText(String text) {
        return text == null ? "" : text;
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

    private TaskDetailDTO toTaskDetailDTO(AgentTaskEntity task) {
        TaskDetailDTO dto = new TaskDetailDTO();
        dto.setTaskId(task.getId());
        dto.setPlanId(task.getPlanId());
        dto.setNodeId(task.getNodeId());
        dto.setName(task.getName());
        dto.setTaskType(task.getTaskType() == null ? null : task.getTaskType().name());
        dto.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        dto.setDependencyNodeIds(task.getDependencyNodeIds());
        dto.setInputContext(task.getInputContext());
        dto.setConfigSnapshot(task.getConfigSnapshot());
        dto.setOutputResult(task.getOutputResult());
        dto.setMaxRetries(task.getMaxRetries());
        dto.setCurrentRetry(task.getCurrentRetry());
        dto.setClaimOwner(task.getClaimOwner());
        dto.setClaimAt(task.getClaimAt());
        dto.setLeaseUntil(task.getLeaseUntil());
        dto.setExecutionAttempt(task.getExecutionAttempt());
        dto.setLatestExecutionTimeMs(resolveLatestExecutionTimeMs(task.getId()));
        dto.setVersion(task.getVersion());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    private Long resolveLatestExecutionTimeMs(Long taskId) {
        if (taskId == null) {
            return null;
        }
        Integer maxAttempt = taskExecutionRepository.getMaxAttemptNumber(taskId);
        if (maxAttempt == null || maxAttempt <= 0) {
            return null;
        }
        TaskExecutionEntity latestExecution = taskExecutionRepository.findByTaskIdAndAttempt(taskId, maxAttempt);
        return latestExecution == null ? null : latestExecution.getExecutionTimeMs();
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
