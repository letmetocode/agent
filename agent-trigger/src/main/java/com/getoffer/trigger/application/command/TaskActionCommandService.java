package com.getoffer.trigger.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.adapter.repository.ITaskShareLinkRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.model.entity.TaskShareLinkEntity;
import com.getoffer.trigger.application.common.TaskDetailViewAssembler;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 任务控制写用例：任务状态控制、导出与分享链接管理。
 */
@Service
public class TaskActionCommandService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IAgentTaskRepository agentTaskRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final ITaskShareLinkRepository taskShareLinkRepository;
    private final ObjectMapper objectMapper;
    private final TaskDetailViewAssembler taskDetailViewAssembler;

    @Value("${app.share.base-url:http://127.0.0.1:8091}")
    private String shareBaseUrl;

    @Value("${app.share.token-salt:agent-share-salt}")
    private String shareTokenSalt;

    @Value("${app.share.max-ttl-hours:168}")
    private Integer maxTtlHours;

    public TaskActionCommandService(IAgentTaskRepository agentTaskRepository,
                                    IAgentPlanRepository agentPlanRepository,
                                    ITaskExecutionRepository taskExecutionRepository,
                                    ITaskShareLinkRepository taskShareLinkRepository,
                                    ObjectMapper objectMapper,
                                    TaskDetailViewAssembler taskDetailViewAssembler) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.taskShareLinkRepository = taskShareLinkRepository;
        this.objectMapper = objectMapper;
        this.taskDetailViewAssembler = taskDetailViewAssembler;
    }

    public TaskDetailDTO pause(Long taskId) {
        AgentTaskEntity task = requireTask(taskId);
        AgentPlanEntity plan = requirePlan(task.getPlanId());
        if (plan.getStatus() != PlanStatusEnum.PAUSED) {
            try {
                plan.pause();
                agentPlanRepository.update(plan);
            } catch (Exception ex) {
                throw illegal("当前计划状态不支持暂停: " + ex.getMessage());
            }
        }
        return taskDetailViewAssembler.toTaskDetailDTO(task);
    }

    public TaskDetailDTO resume(Long taskId) {
        AgentTaskEntity task = requireTask(taskId);
        AgentPlanEntity plan = requirePlan(task.getPlanId());
        if (plan.getStatus() != PlanStatusEnum.RUNNING) {
            try {
                plan.resume();
                agentPlanRepository.update(plan);
            } catch (Exception ex) {
                throw illegal("当前计划状态不支持恢复: " + ex.getMessage());
            }
        }
        return taskDetailViewAssembler.toTaskDetailDTO(task);
    }

    public TaskDetailDTO cancel(Long taskId) {
        AgentTaskEntity task = requireTask(taskId);
        AgentPlanEntity plan = requirePlan(task.getPlanId());
        if (plan.getStatus() != PlanStatusEnum.CANCELLED) {
            try {
                plan.cancel();
                agentPlanRepository.update(plan);
            } catch (Exception ex) {
                throw illegal("当前计划状态不支持取消: " + ex.getMessage());
            }
        }
        return taskDetailViewAssembler.toTaskDetailDTO(task);
    }

    public TaskDetailDTO retryFromFailed(Long taskId) {
        AgentTaskEntity task = requireTask(taskId);
        if (task.getStatus() != TaskStatusEnum.FAILED) {
            throw illegal("仅 FAILED 任务支持重试");
        }
        AgentPlanEntity plan = requirePlan(task.getPlanId());
        if (plan.getStatus() == PlanStatusEnum.CANCELLED || plan.getStatus() == PlanStatusEnum.COMPLETED) {
            throw illegal("计划已结束，不支持从失败节点重试");
        }

        task.rollbackToReady();
        task.setOutputResult(null);
        task.setClaimOwner(null);
        task.setClaimAt(null);
        task.setLeaseUntil(null);
        if (task.getCurrentRetry() != null
                && task.getMaxRetries() != null
                && task.getCurrentRetry() >= task.getMaxRetries()) {
            task.setCurrentRetry(Math.max(0, task.getMaxRetries() - 1));
        }
        AgentTaskEntity updatedTask = agentTaskRepository.update(task);

        if (plan.getStatus() == PlanStatusEnum.FAILED || plan.getStatus() == PlanStatusEnum.PAUSED) {
            plan.setStatus(PlanStatusEnum.RUNNING);
            plan.setErrorSummary(null);
            plan.setUpdatedAt(LocalDateTime.now());
            agentPlanRepository.update(plan);
        }

        return taskDetailViewAssembler.toTaskDetailDTO(updatedTask);
    }

    public Map<String, Object> exportTask(Long taskId, String format) {
        AgentTaskEntity task = requireTask(taskId);
        String normalizedFormat = format == null ? "markdown" : format.trim().toLowerCase();
        if (!"markdown".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
            throw illegal("仅支持 markdown/json 导出");
        }

        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("task", taskDetailViewAssembler.toTaskDetailDTO(task));
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
            throw illegal("导出内容生成失败: " + ex.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", fileName);
        result.put("contentType", contentType);
        result.put("content", content);
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> createShareLink(Long taskId, Integer expiresHours) {
        requireTask(taskId);
        int ttlHours = normalizeTtlHours(expiresHours);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(ttlHours);

        String token = generateToken();
        String shareCode = generateShareCode();

        TaskShareLinkEntity entity = new TaskShareLinkEntity();
        entity.setTaskId(taskId);
        entity.setShareCode(shareCode);
        entity.setTokenHash(hashToken(token));
        entity.setScope("RESULT_AND_REFERENCES");
        entity.setExpiresAt(expiresAt);
        entity.setRevoked(false);
        entity.setCreatedBy("system");
        entity.setVersion(0);

        TaskShareLinkEntity saved = taskShareLinkRepository.save(entity);
        String shareUrl = buildShareUrl(taskId, saved.getShareCode(), token);

        Map<String, Object> result = toShareLinkItem(saved, taskId, false);
        result.put("shareUrl", shareUrl);
        result.put("token", token);
        return result;
    }

    public List<Map<String, Object>> listShareLinks(Long taskId) {
        requireTask(taskId);
        List<TaskShareLinkEntity> links = taskShareLinkRepository.findByTaskId(taskId);
        List<Map<String, Object>> result = new ArrayList<>();
        if (links != null) {
            for (TaskShareLinkEntity link : links) {
                result.add(toShareLinkItem(link, taskId, true));
            }
        }
        return result;
    }

    public Map<String, Object> revokeShareLink(Long taskId, Long shareId, String reason) {
        requireTask(taskId);
        if (shareId == null || shareId <= 0) {
            throw illegal("分享链接ID不能为空");
        }

        String normalizedReason = StringUtils.isBlank(reason) ? "MANUAL_REVOKE" : reason.trim();
        boolean updated = taskShareLinkRepository.revokeById(taskId, shareId, normalizedReason);
        if (!updated) {
            throw illegal("分享链接不存在或已撤销");
        }

        TaskShareLinkEntity latest = taskShareLinkRepository.findById(shareId);
        if (latest == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("shareId", shareId);
            result.put("revoked", true);
            result.put("revokedAt", LocalDateTime.now());
            return result;
        }
        return toShareLinkItem(latest, taskId, true);
    }

    public Map<String, Object> revokeAllShareLinks(Long taskId, String reason) {
        requireTask(taskId);
        String normalizedReason = StringUtils.isBlank(reason) ? "MANUAL_REVOKE_ALL" : reason.trim();
        int revokedCount = taskShareLinkRepository.revokeAllByTaskId(taskId, normalizedReason);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("revokedCount", revokedCount);
        result.put("revokedAt", LocalDateTime.now());
        return result;
    }

    private AgentTaskEntity requireTask(Long taskId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            throw illegal("任务不存在");
        }
        return task;
    }

    private AgentPlanEntity requirePlan(Long planId) {
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            throw illegal("任务关联计划不存在");
        }
        return plan;
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

    private int normalizeTtlHours(Integer expiresHours) {
        int limit = maxTtlHours == null || maxTtlHours <= 0 ? 168 : maxTtlHours;
        int ttlHours = expiresHours == null ? 24 : expiresHours;
        return Math.max(1, Math.min(limit, ttlHours));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateShareCode() {
        byte[] bytes = new byte[9];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = token + ":" + StringUtils.defaultString(shareTokenSalt);
            byte[] hashed = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String buildShareUrl(Long taskId, String shareCode, String token) {
        String normalizedBaseUrl = shareBaseUrl == null ? "" : shareBaseUrl.replaceAll("/$", "");
        return String.format("%s/share/tasks/%d?code=%s&token=%s", normalizedBaseUrl, taskId, shareCode, token);
    }

    private Map<String, Object> toShareLinkItem(TaskShareLinkEntity link, Long taskId, boolean includeSharePreviewUrl) {
        Map<String, Object> item = new HashMap<>();
        item.put("shareId", link.getId());
        item.put("taskId", taskId);
        item.put("shareCode", link.getShareCode());
        item.put("scope", link.getScope());
        item.put("status", resolveShareStatus(link));
        item.put("expiresAt", link.getExpiresAt());
        item.put("revoked", Boolean.TRUE.equals(link.getRevoked()));
        item.put("revokedAt", link.getRevokedAt());
        item.put("revokedReason", link.getRevokedReason());
        item.put("createdAt", link.getCreatedAt());
        item.put("updatedAt", link.getUpdatedAt());
        if (includeSharePreviewUrl) {
            String normalizedBaseUrl = shareBaseUrl == null ? "" : shareBaseUrl.replaceAll("/$", "");
            item.put("shareUrl", String.format("%s/share/tasks/%d?code=%s", normalizedBaseUrl, taskId, link.getShareCode()));
        }
        return item;
    }

    private String resolveShareStatus(TaskShareLinkEntity link) {
        if (link == null) {
            return "UNKNOWN";
        }
        if (Boolean.TRUE.equals(link.getRevoked())) {
            return "REVOKED";
        }
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(LocalDateTime.now())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private AppException illegal(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
