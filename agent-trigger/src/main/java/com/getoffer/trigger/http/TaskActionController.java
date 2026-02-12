package com.getoffer.trigger.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务控制与产物导出 API。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskActionController {

    private final IAgentTaskRepository agentTaskRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.share.base-url:http://127.0.0.1:8091}")
    private String shareBaseUrl;

    public TaskActionController(IAgentTaskRepository agentTaskRepository,
                                IAgentPlanRepository agentPlanRepository,
                                ITaskExecutionRepository taskExecutionRepository,
                                ObjectMapper objectMapper) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}/pause")
    public Response<TaskDetailDTO> pause(@PathVariable("id") Long taskId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
        if (plan == null) {
            return illegal("任务关联计划不存在");
        }
        if (plan.getStatus() == PlanStatusEnum.PAUSED) {
            return success(toTaskDetailDTO(task));
        }
        try {
            plan.pause();
            agentPlanRepository.update(plan);
            return success(toTaskDetailDTO(task));
        } catch (Exception ex) {
            return illegal("当前计划状态不支持暂停: " + ex.getMessage());
        }
    }

    @PostMapping("/{id}/resume")
    public Response<TaskDetailDTO> resume(@PathVariable("id") Long taskId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
        if (plan == null) {
            return illegal("任务关联计划不存在");
        }
        if (plan.getStatus() == PlanStatusEnum.RUNNING) {
            return success(toTaskDetailDTO(task));
        }
        try {
            plan.resume();
            agentPlanRepository.update(plan);
            return success(toTaskDetailDTO(task));
        } catch (Exception ex) {
            return illegal("当前计划状态不支持恢复: " + ex.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public Response<TaskDetailDTO> cancel(@PathVariable("id") Long taskId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
        if (plan == null) {
            return illegal("任务关联计划不存在");
        }
        if (plan.getStatus() == PlanStatusEnum.CANCELLED) {
            return success(toTaskDetailDTO(task));
        }
        try {
            plan.cancel();
            agentPlanRepository.update(plan);
            return success(toTaskDetailDTO(task));
        } catch (Exception ex) {
            return illegal("当前计划状态不支持取消: " + ex.getMessage());
        }
    }

    @PostMapping("/{id}/retry-from-failed")
    public Response<TaskDetailDTO> retryFromFailed(@PathVariable("id") Long taskId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        if (task.getStatus() != TaskStatusEnum.FAILED) {
            return illegal("仅 FAILED 任务支持重试");
        }

        AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
        if (plan == null) {
            return illegal("任务关联计划不存在");
        }
        if (plan.getStatus() == PlanStatusEnum.CANCELLED || plan.getStatus() == PlanStatusEnum.COMPLETED) {
            return illegal("计划已结束，不支持从失败节点重试");
        }

        task.rollbackToReady();
        task.setOutputResult(null);
        task.setClaimOwner(null);
        task.setClaimAt(null);
        task.setLeaseUntil(null);

        if (task.getCurrentRetry() != null && task.getMaxRetries() != null && task.getCurrentRetry() >= task.getMaxRetries()) {
            task.setCurrentRetry(Math.max(0, task.getMaxRetries() - 1));
        }

        AgentTaskEntity updatedTask = agentTaskRepository.update(task);

        if (plan.getStatus() == PlanStatusEnum.FAILED || plan.getStatus() == PlanStatusEnum.PAUSED) {
            plan.setStatus(PlanStatusEnum.RUNNING);
            plan.setErrorSummary(null);
            plan.setUpdatedAt(LocalDateTime.now());
            agentPlanRepository.update(plan);
        }

        return success(toTaskDetailDTO(updatedTask));
    }

    @GetMapping("/{id}/export")
    public Response<Map<String, Object>> exportTask(@PathVariable("id") Long taskId,
                                                    @RequestParam(value = "format", required = false) String format) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }
        String normalizedFormat = format == null ? "markdown" : format.trim().toLowerCase();
        if (!"markdown".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
            return illegal("仅支持 markdown/json 导出");
        }

        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("task", toTaskDetailDTO(task));
        payload.put("executions", executions == null ? new ArrayList<>() : executions);
        payload.put("generatedAt", LocalDateTime.now());

        String content;
        String contentType;
        String fileName;
        try {
            if ("json".equals(normalizedFormat)) {
                content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
                contentType = "application/json";
                fileName = String.format("task-%d.json", taskId);
            } else {
                content = toMarkdownContent(task, executions);
                contentType = "text/markdown";
                fileName = String.format("task-%d.md", taskId);
            }
        } catch (JsonProcessingException ex) {
            return illegal("导出内容生成失败: " + ex.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", fileName);
        result.put("contentType", contentType);
        result.put("content", content);
        result.put("generatedAt", LocalDateTime.now());
        return success(result);
    }

    @PostMapping("/{id}/share-links")
    public Response<Map<String, Object>> createShareLinks(@PathVariable("id") Long taskId,
                                                           @RequestParam(value = "expiresHours", required = false) Integer expiresHours) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("任务不存在");
        }

        int ttlHours = expiresHours == null ? 24 : Math.max(1, Math.min(168, expiresHours));
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(ttlHours);
        String raw = String.format("task:%d:exp:%s:ver:%d", taskId, expiresAt, task.getVersion() == null ? 0 : task.getVersion());
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String normalizedBaseUrl = shareBaseUrl == null ? "" : shareBaseUrl.replaceAll("/$", "");
        String shareUrl = String.format("%s/share/tasks/%d?token=%s", normalizedBaseUrl, taskId, token);

        Map<String, Object> result = new HashMap<>();
        result.put("shareUrl", shareUrl);
        result.put("token", token);
        result.put("expiresAt", expiresAt);
        return success(result);
    }

    private String toMarkdownContent(AgentTaskEntity task, List<TaskExecutionEntity> executions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 任务导出\n\n");
        sb.append("- Task ID: ").append(task.getId()).append("\n");
        sb.append("- Plan ID: ").append(task.getPlanId()).append("\n");
        sb.append("- 节点: ").append(task.getNodeId()).append("\n");
        sb.append("- 名称: ").append(task.getName() == null ? "-" : task.getName()).append("\n");
        sb.append("- 状态: ").append(task.getStatus() == null ? "-" : task.getStatus().name()).append("\n\n");
        sb.append("## 输出\n\n");
        sb.append(task.getOutputResult() == null ? "（无）" : task.getOutputResult()).append("\n\n");
        sb.append("## 执行记录\n\n");
        if (executions == null || executions.isEmpty()) {
            sb.append("- 无执行记录\n");
        } else {
            for (TaskExecutionEntity item : executions) {
                sb.append("- attempt ").append(item.getAttemptNumber())
                        .append(" | model=").append(item.getModelName() == null ? "-" : item.getModelName())
                        .append(" | timeMs=").append(item.getExecutionTimeMs() == null ? 0 : item.getExecutionTimeMs())
                        .append(" | error=").append(item.getErrorType() == null ? "-" : item.getErrorType())
                        .append("\n");
            }
        }
        return sb.toString();
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
        if (latestExecution == null) {
            return null;
        }
        return latestExecution.getExecutionTimeMs();
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
