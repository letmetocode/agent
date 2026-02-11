package com.getoffer.trigger.http;

import com.getoffer.api.dto.PlanDetailDTO;
import com.getoffer.api.dto.PlanSummaryDTO;
import com.getoffer.api.dto.PlanTaskStatsDTO;
import com.getoffer.api.dto.SessionDetailDTO;
import com.getoffer.api.dto.SessionOverviewDTO;
import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.api.dto.TaskExecutionDetailDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    public QueryController(IAgentSessionRepository agentSessionRepository,
                           IAgentPlanRepository agentPlanRepository,
                           IAgentTaskRepository agentTaskRepository,
                           ITaskExecutionRepository taskExecutionRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
    }

    @GetMapping("/sessions/{id}")
    public Response<SessionDetailDTO> getSession(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return illegal("SessionId不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return illegal("会话不存在");
        }
        return success(toSessionDetailDTO(session));
    }

    @GetMapping("/sessions/{id}/plans")
    public Response<List<PlanSummaryDTO>> listSessionPlans(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return illegal("SessionId不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return illegal("会话不存在");
        }
        List<AgentPlanEntity> plans = agentPlanRepository.findBySessionId(sessionId);
        List<PlanSummaryDTO> data = plans == null ? Collections.emptyList() : plans.stream()
                .map(this::toPlanSummaryDTO)
                .collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/sessions/{id}/overview")
    public Response<SessionOverviewDTO> getSessionOverview(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return illegal("SessionId不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return illegal("会话不存在");
        }

        List<AgentPlanEntity> plans = agentPlanRepository.findBySessionId(sessionId);
        List<PlanSummaryDTO> planDtos = plans == null ? Collections.emptyList() : plans.stream()
                .sorted(Comparator.comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPlanSummaryDTO)
                .collect(Collectors.toList());

        SessionOverviewDTO overview = new SessionOverviewDTO();
        overview.setSession(toSessionDetailDTO(session));
        overview.setPlans(planDtos);
        if (!planDtos.isEmpty()) {
            Long latestPlanId = planDtos.get(0).getPlanId();
            overview.setLatestPlanId(latestPlanId);
            List<AgentTaskEntity> latestPlanTasks = agentTaskRepository.findByPlanId(latestPlanId);
            overview.setLatestPlanTaskStats(toTaskStats(latestPlanTasks));
            List<TaskDetailDTO> taskDtos = latestPlanTasks == null ? Collections.emptyList() : latestPlanTasks.stream()
                    .map(this::toTaskDetailDTO)
                    .collect(Collectors.toList());
            overview.setLatestPlanTasks(taskDtos);
        } else {
            overview.setLatestPlanTasks(Collections.emptyList());
            overview.setLatestPlanTaskStats(new PlanTaskStatsDTO());
        }
        return success(overview);
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
        List<TaskDetailDTO> data = tasks == null ? Collections.emptyList() : tasks.stream()
                .map(this::toTaskDetailDTO)
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

    private SessionDetailDTO toSessionDetailDTO(AgentSessionEntity session) {
        SessionDetailDTO dto = new SessionDetailDTO();
        dto.setSessionId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setTitle(session.getTitle());
        dto.setActive(session.getIsActive());
        dto.setMetaInfo(session.getMetaInfo());
        dto.setCreatedAt(session.getCreatedAt());
        return dto;
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
        dto.setVersion(task.getVersion());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
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

    private PlanTaskStatsDTO toTaskStats(List<AgentTaskEntity> tasks) {
        PlanTaskStatsDTO dto = new PlanTaskStatsDTO();
        if (tasks == null || tasks.isEmpty()) {
            dto.setTotal(0L);
            dto.setPending(0L);
            dto.setReady(0L);
            dto.setRunningLike(0L);
            dto.setCompleted(0L);
            dto.setFailed(0L);
            dto.setSkipped(0L);
            return dto;
        }
        long pending = 0L;
        long ready = 0L;
        long runningLike = 0L;
        long completed = 0L;
        long failed = 0L;
        long skipped = 0L;
        for (AgentTaskEntity task : tasks) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            TaskStatusEnum status = task.getStatus();
            if (status == TaskStatusEnum.PENDING) {
                pending++;
            } else if (status == TaskStatusEnum.READY) {
                ready++;
            } else if (status == TaskStatusEnum.RUNNING
                    || status == TaskStatusEnum.VALIDATING
                    || status == TaskStatusEnum.REFINING) {
                runningLike++;
            } else if (status == TaskStatusEnum.COMPLETED) {
                completed++;
            } else if (status == TaskStatusEnum.FAILED) {
                failed++;
            } else if (status == TaskStatusEnum.SKIPPED) {
                skipped++;
            }
        }
        dto.setTotal((long) tasks.size());
        dto.setPending(pending);
        dto.setReady(ready);
        dto.setRunningLike(runningLike);
        dto.setCompleted(completed);
        dto.setFailed(failed);
        dto.setSkipped(skipped);
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
