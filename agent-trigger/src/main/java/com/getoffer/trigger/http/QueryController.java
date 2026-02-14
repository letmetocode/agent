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
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public Response<List<Map<String, Object>>> listAgentTools() {
        List<AgentToolCatalogEntity> tools = agentToolCatalogRepository.findAll();
        List<Map<String, Object>> data = tools == null ? Collections.emptyList() : tools.stream()
                .map(this::toAgentTool)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/agents/vector-stores")
    public Response<List<Map<String, Object>>> listVectorStores() {
        List<VectorStoreRegistryEntity> stores = vectorStoreRegistryRepository.findAll();
        List<Map<String, Object>> data = stores == null ? Collections.emptyList() : stores.stream()
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

        List<AgentTaskEntity> tasks = agentTaskRepository.findAll();
        List<AgentPlanEntity> plans = agentPlanRepository.findAll();
        List<AgentSessionEntity> sessions = agentSessionRepository.findAll();

        Map<String, Object> taskStats = buildTaskStats(tasks);
        Map<String, Object> planStats = buildPlanStats(plans);
        Map<String, Object> sessionStats = new HashMap<>();
        sessionStats.put("total", sessions == null ? 0 : sessions.size());
        sessionStats.put("active", sessions == null ? 0 : sessions.stream().filter(item -> Boolean.TRUE.equals(item.getIsActive())).count());

        List<TaskExecutionEntity> executions = taskExecutionRepository.findAll();
        Map<String, Object> latencyStats = buildLatencyStats(executions);
        long slowTaskCount = countExecutionsAbove(executions, 30_000L);
        long slaBreachCount = countExecutionsAbove(executions, 120_000L);

        List<AgentTaskEntity> recentTaskEntities = (tasks == null ? Collections.<AgentTaskEntity>emptyList() : tasks).stream()
                .sorted(Comparator.comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(normalizedTaskLimit)
                .collect(Collectors.toList());

        List<AgentTaskEntity> recentFailedTaskEntities = (tasks == null ? Collections.<AgentTaskEntity>emptyList() : tasks).stream()
                .filter(item -> item.getStatus() == TaskStatusEnum.FAILED)
                .sorted(Comparator.comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(normalizedTaskLimit)
                .collect(Collectors.toList());

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

        List<PlanSummaryDTO> recentPlans = (plans == null ? Collections.<AgentPlanEntity>emptyList() : plans).stream()
                .sorted(Comparator.comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
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

    private Map<String, Object> buildTaskStats(List<AgentTaskEntity> tasks) {
        Map<String, Object> stats = new HashMap<>();
        long total = tasks == null ? 0L : tasks.size();
        long pending = countTasks(tasks, TaskStatusEnum.PENDING);
        long ready = countTasks(tasks, TaskStatusEnum.READY);
        long running = countTasks(tasks, TaskStatusEnum.RUNNING) + countTasks(tasks, TaskStatusEnum.VALIDATING) + countTasks(tasks, TaskStatusEnum.REFINING);
        long completed = countTasks(tasks, TaskStatusEnum.COMPLETED);
        long failed = countTasks(tasks, TaskStatusEnum.FAILED);
        long skipped = countTasks(tasks, TaskStatusEnum.SKIPPED);
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("ready", ready);
        stats.put("runningLike", running);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("skipped", skipped);
        return stats;
    }

    private Map<String, Object> buildPlanStats(List<AgentPlanEntity> plans) {
        Map<String, Object> stats = new HashMap<>();
        long total = plans == null ? 0L : plans.size();
        stats.put("total", total);
        stats.put("planning", countPlans(plans, PlanStatusEnum.PLANNING));
        stats.put("ready", countPlans(plans, PlanStatusEnum.READY));
        stats.put("running", countPlans(plans, PlanStatusEnum.RUNNING));
        stats.put("paused", countPlans(plans, PlanStatusEnum.PAUSED));
        stats.put("completed", countPlans(plans, PlanStatusEnum.COMPLETED));
        stats.put("failed", countPlans(plans, PlanStatusEnum.FAILED));
        stats.put("cancelled", countPlans(plans, PlanStatusEnum.CANCELLED));
        return stats;
    }

    private long countTasks(List<AgentTaskEntity> tasks, TaskStatusEnum status) {
        if (tasks == null || tasks.isEmpty()) {
            return 0L;
        }
        return tasks.stream().filter(item -> item != null && item.getStatus() == status).count();
    }

    private long countPlans(List<AgentPlanEntity> plans, PlanStatusEnum status) {
        if (plans == null || plans.isEmpty()) {
            return 0L;
        }
        return plans.stream().filter(item -> item != null && item.getStatus() == status).count();
    }

    private Map<String, Object> buildLatencyStats(List<TaskExecutionEntity> executions) {
        List<Long> times = (executions == null ? Collections.<TaskExecutionEntity>emptyList() : executions).stream()
                .map(TaskExecutionEntity::getExecutionTimeMs)
                .filter(Objects::nonNull)
                .filter(item -> item > 0)
                .sorted()
                .collect(Collectors.toList());
        Map<String, Object> stats = new HashMap<>();
        stats.put("p50", percentile(times, 0.50));
        stats.put("p95", percentile(times, 0.95));
        stats.put("p99", percentile(times, 0.99));
        return stats;
    }

    private long countExecutionsAbove(List<TaskExecutionEntity> executions, long thresholdMs) {
        if (executions == null || executions.isEmpty()) {
            return 0L;
        }
        return executions.stream()
                .map(TaskExecutionEntity::getExecutionTimeMs)
                .filter(Objects::nonNull)
                .filter(item -> item >= thresholdMs)
                .count();
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0L;
        }
        int size = sortedValues.size();
        int index = (int) Math.ceil(percentile * size) - 1;
        int normalizedIndex = Math.max(0, Math.min(size - 1, index));
        return sortedValues.get(normalizedIndex);
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
