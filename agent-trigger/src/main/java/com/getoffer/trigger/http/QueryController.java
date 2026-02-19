package com.getoffer.trigger.http;

import com.getoffer.api.dto.PlanDetailDTO;
import com.getoffer.api.dto.PlanSummaryDTO;
import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.api.dto.TaskExecutionDetailDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.trigger.application.common.TaskDetailViewAssembler;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 页面只读查询 API。
 */
@RestController
@RequestMapping("/api")
public class QueryController {

    private final IAgentSessionRepository agentSessionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IPlanTaskEventRepository planTaskEventRepository;
    private final IAgentToolCatalogRepository agentToolCatalogRepository;
    private final IVectorStoreRegistryRepository vectorStoreRegistryRepository;
    private final TaskDetailViewAssembler taskDetailViewAssembler;

    public QueryController(IAgentSessionRepository agentSessionRepository,
                           IAgentPlanRepository agentPlanRepository,
                           IAgentTaskRepository agentTaskRepository,
                           ITaskExecutionRepository taskExecutionRepository,
                           IPlanTaskEventRepository planTaskEventRepository,
                           IAgentToolCatalogRepository agentToolCatalogRepository,
                           IVectorStoreRegistryRepository vectorStoreRegistryRepository,
                           TaskDetailViewAssembler taskDetailViewAssembler) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.planTaskEventRepository = planTaskEventRepository;
        this.agentToolCatalogRepository = agentToolCatalogRepository;
        this.vectorStoreRegistryRepository = vectorStoreRegistryRepository;
        this.taskDetailViewAssembler = taskDetailViewAssembler;
    }

    @GetMapping("/plans/{id}")
    public Response<PlanDetailDTO> getPlan(@PathVariable("id") Long planId) {
        if (planId == null) {
            return illegal("PlanId不能为空");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            return illegal("计划不存在");
        }
        return success(toPlanDetailDTO(plan));
    }

    @GetMapping("/plans/{id}/tasks")
    public Response<List<TaskDetailDTO>> listPlanTasks(@PathVariable("id") Long planId) {
        if (planId == null) {
            return illegal("PlanId不能为空");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            return illegal("计划不存在");
        }
        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(planId);
        Map<Long, Long> latestExecutionTimeMap = taskDetailViewAssembler.resolveLatestExecutionTimeMap(tasks);
        List<TaskDetailDTO> data = tasks == null ? Collections.emptyList() : tasks.stream()
                .map(task -> taskDetailViewAssembler.toTaskDetailDTO(task, latestExecutionTimeMap))
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/tasks/{id}/executions")
    public Response<List<TaskExecutionDetailDTO>> listTaskExecutions(@PathVariable("id") Long taskId) {
        if (taskId == null) {
            return illegal("TaskId不能为空");
        }
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);
        List<TaskExecutionDetailDTO> data = executions == null ? Collections.emptyList() : executions.stream()
                .map(this::toTaskExecutionDetailDTO)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/tasks/{id}")
    public Response<TaskDetailDTO> getTask(@PathVariable("id") Long taskId) {
        if (taskId == null) {
            return illegal("TaskId不能为空");
        }
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        return success(taskDetailViewAssembler.toTaskDetailDTO(task));
    }

    @GetMapping("/plans/{id}/events")
    public Response<List<Map<String, Object>>> listPlanEvents(@PathVariable("id") Long planId,
                                                               @RequestParam(value = "afterEventId", required = false) Long afterEventId,
                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        if (planId == null) {
            return illegal("PlanId不能为空");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            return illegal("计划不存在");
        }

        long cursor = afterEventId == null ? 0L : Math.max(0L, afterEventId);
        int normalizedLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 500));
        List<PlanTaskEventEntity> events = planTaskEventRepository.findByPlanIdAfterEventId(planId, cursor, normalizedLimit);
        List<Map<String, Object>> data = events == null ? Collections.emptyList() : events.stream()
                .map(this::toPlanEvent)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/agents/tools")
    public Response<List<Map<String, Object>>> listAgentTools(@RequestParam(value = "limit", required = false) Integer limit) {
        int normalizedLimit = normalizeLimit(limit, 100, 500);
        List<AgentToolCatalogEntity> tools = safeList(agentToolCatalogRepository.findRecent(normalizedLimit));
        List<Map<String, Object>> data = tools.stream()
                .map(this::toAgentTool)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/agents/vector-stores")
    public Response<List<Map<String, Object>>> listVectorStores(@RequestParam(value = "limit", required = false) Integer limit) {
        int normalizedLimit = normalizeLimit(limit, 100, 500);
        List<VectorStoreRegistryEntity> stores = safeList(vectorStoreRegistryRepository.findRecent(normalizedLimit));
        List<Map<String, Object>> data = stores.stream()
                .map(this::toVectorStore)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/dashboard/overview")
    public Response<Map<String, Object>> getDashboardOverview(
            @RequestParam(value = "taskLimit", required = false) Integer taskLimit,
            @RequestParam(value = "planLimit", required = false) Integer planLimit) {
        int normalizedTaskLimit = taskLimit == null ? 10 : Math.max(1, Math.min(taskLimit, 100));
        int normalizedPlanLimit = planLimit == null ? 10 : Math.max(1, Math.min(planLimit, 100));

        Map<String, Object> taskStats = buildTaskStats();
        Map<String, Object> planStats = buildPlanStats();
        Map<String, Object> sessionStats = new HashMap<>();
        sessionStats.put("total", agentSessionRepository.countAll());
        sessionStats.put("active", agentSessionRepository.countByActive(true));

        Map<String, Long> quantiles = taskExecutionRepository.summarizeLatencyQuantiles();
        Map<String, Object> latencyStats = new HashMap<>();
        latencyStats.put("p50", quantiles.getOrDefault("p50", 0L));
        latencyStats.put("p95", quantiles.getOrDefault("p95", 0L));
        latencyStats.put("p99", quantiles.getOrDefault("p99", 0L));
        long slowTaskCount = taskExecutionRepository.countByExecutionTimeAbove(30_000L);
        long slaBreachCount = taskExecutionRepository.countByExecutionTimeAbove(120_000L);

        List<AgentTaskEntity> recentTaskEntities = safeList(agentTaskRepository.findRecent(normalizedTaskLimit));
        List<AgentTaskEntity> recentFailedTaskEntities = safeList(agentTaskRepository.findRecentByStatus(TaskStatusEnum.FAILED, normalizedTaskLimit));

        List<AgentTaskEntity> taskProjection = Stream.concat(recentTaskEntities.stream(), recentFailedTaskEntities.stream())
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(AgentTaskEntity::getId, item -> item, (left, right) -> left, java.util.LinkedHashMap::new))
                .values().stream()
                .collect(Collectors.toList());
        Map<Long, Long> latestExecutionTimeMap = taskDetailViewAssembler.resolveLatestExecutionTimeMap(taskProjection);

        List<TaskDetailDTO> recentTasks = recentTaskEntities.stream()
                .map(task -> taskDetailViewAssembler.toTaskDetailDTO(task, latestExecutionTimeMap))
                .collect(Collectors.toList());

        List<TaskDetailDTO> recentFailedTasks = recentFailedTaskEntities.stream()
                .map(task -> taskDetailViewAssembler.toTaskDetailDTO(task, latestExecutionTimeMap))
                .collect(Collectors.toList());

        List<PlanSummaryDTO> recentPlans = safeList(agentPlanRepository.findRecent(normalizedPlanLimit)).stream()
                .limit(normalizedPlanLimit)
                .map(this::toPlanSummaryDTO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("taskStats", taskStats);
        result.put("planStats", planStats);
        result.put("sessionStats", sessionStats);
        result.put("recentTasks", recentTasks);
        result.put("recentFailedTasks", recentFailedTasks);
        result.put("recentPlans", recentPlans);
        result.put("latencyStats", latencyStats);
        result.put("slowTaskCount", slowTaskCount);
        result.put("slaBreachCount", slaBreachCount);
        return success(result);
    }

    private PlanSummaryDTO toPlanSummaryDTO(AgentPlanEntity plan) {
        PlanSummaryDTO dto = new PlanSummaryDTO();
        dto.setPlanId(plan.getId());
        dto.setSessionId(plan.getSessionId());
        dto.setPlanGoal(plan.getPlanGoal());
        dto.setStatus(plan.getStatus() == null ? null : plan.getStatus().name());
        dto.setPriority(plan.getPriority());
        dto.setErrorSummary(plan.getErrorSummary());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        return dto;
    }

    private PlanDetailDTO toPlanDetailDTO(AgentPlanEntity plan) {
        PlanDetailDTO dto = new PlanDetailDTO();
        dto.setPlanId(plan.getId());
        dto.setSessionId(plan.getSessionId());
        dto.setRouteDecisionId(plan.getRouteDecisionId());
        dto.setWorkflowDefinitionId(plan.getWorkflowDefinitionId());
        dto.setWorkflowDraftId(plan.getWorkflowDraftId());
        dto.setPlanGoal(plan.getPlanGoal());
        dto.setExecutionGraph(plan.getExecutionGraph());
        dto.setDefinitionSnapshot(plan.getDefinitionSnapshot());
        dto.setGlobalContext(plan.getGlobalContext());
        dto.setStatus(plan.getStatus() == null ? null : plan.getStatus().name());
        dto.setPriority(plan.getPriority());
        dto.setErrorSummary(plan.getErrorSummary());
        dto.setVersion(plan.getVersion());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        return dto;
    }

    private TaskExecutionDetailDTO toTaskExecutionDetailDTO(TaskExecutionEntity execution) {
        TaskExecutionDetailDTO dto = new TaskExecutionDetailDTO();
        dto.setExecutionId(execution.getId());
        dto.setTaskId(execution.getTaskId());
        dto.setAttemptNumber(execution.getAttemptNumber());
        dto.setPromptSnapshot(execution.getPromptSnapshot());
        dto.setLlmResponseRaw(execution.getLlmResponseRaw());
        dto.setModelName(execution.getModelName());
        dto.setTokenUsage(execution.getTokenUsage());
        dto.setExecutionTimeMs(execution.getExecutionTimeMs());
        dto.setValid(execution.getIsValid());
        dto.setValidationFeedback(execution.getValidationFeedback());
        dto.setErrorMessage(execution.getErrorMessage());
        dto.setErrorType(execution.getErrorType());
        dto.setCreatedAt(execution.getCreatedAt());
        return dto;
    }

    private Map<String, Object> toPlanEvent(PlanTaskEventEntity event) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", event.getId());
        dto.put("planId", event.getPlanId());
        dto.put("taskId", event.getTaskId());
        dto.put("eventType", event.getEventType());
        dto.put("eventName", event.getEventType() == null ? null : event.getEventType().getEventName());
        dto.put("eventData", event.getEventData());
        dto.put("createdAt", event.getCreatedAt());
        return dto;
    }

    private Map<String, Object> buildTaskStats() {
        Map<String, Object> stats = new HashMap<>();
        long total = agentTaskRepository.countAll();
        long pending = agentTaskRepository.countByStatus(TaskStatusEnum.PENDING);
        long ready = agentTaskRepository.countByStatus(TaskStatusEnum.READY);
        long running = agentTaskRepository.countByStatus(TaskStatusEnum.RUNNING)
                + agentTaskRepository.countByStatus(TaskStatusEnum.VALIDATING)
                + agentTaskRepository.countByStatus(TaskStatusEnum.REFINING);
        long completed = agentTaskRepository.countByStatus(TaskStatusEnum.COMPLETED);
        long failed = agentTaskRepository.countByStatus(TaskStatusEnum.FAILED);
        long skipped = agentTaskRepository.countByStatus(TaskStatusEnum.SKIPPED);
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("ready", ready);
        stats.put("runningLike", running);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("skipped", skipped);
        return stats;
    }

    private Map<String, Object> buildPlanStats() {
        Map<String, Object> stats = new HashMap<>();
        long total = agentPlanRepository.countAll();
        stats.put("total", total);
        stats.put("planning", agentPlanRepository.countByStatus(PlanStatusEnum.PLANNING));
        stats.put("ready", agentPlanRepository.countByStatus(PlanStatusEnum.READY));
        stats.put("running", agentPlanRepository.countByStatus(PlanStatusEnum.RUNNING));
        stats.put("paused", agentPlanRepository.countByStatus(PlanStatusEnum.PAUSED));
        stats.put("completed", agentPlanRepository.countByStatus(PlanStatusEnum.COMPLETED));
        stats.put("failed", agentPlanRepository.countByStatus(PlanStatusEnum.FAILED));
        stats.put("cancelled", agentPlanRepository.countByStatus(PlanStatusEnum.CANCELLED));
        return stats;
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? Collections.emptyList() : source;
    }

    private int normalizeLimit(Integer limit, int defaultValue, int maxValue) {
        return limit == null ? defaultValue : Math.max(1, Math.min(limit, maxValue));
    }

    private Map<String, Object> toAgentTool(AgentToolCatalogEntity tool) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", tool.getId());
        dto.put("name", tool.getName());
        dto.put("type", tool.getType());
        dto.put("description", tool.getDescription());
        dto.put("isActive", tool.getIsActive());
        dto.put("toolConfig", tool.getToolConfig());
        dto.put("inputSchema", tool.getInputSchema());
        dto.put("outputSchema", tool.getOutputSchema());
        dto.put("createdAt", tool.getCreatedAt());
        dto.put("updatedAt", tool.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toVectorStore(VectorStoreRegistryEntity store) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", store.getId());
        dto.put("name", store.getName());
        dto.put("storeType", store.getStoreType());
        dto.put("collectionName", store.getCollectionName());
        dto.put("dimension", store.getDimension());
        dto.put("isActive", store.getIsActive());
        dto.put("connectionConfig", store.getConnectionConfig());
        dto.put("createdAt", store.getCreatedAt());
        dto.put("updatedAt", store.getUpdatedAt());
        return dto;
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
