package com.getoffer.trigger.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 运行时执行支撑：承载执行器运行态能力，减少 TaskExecutor 的非调度职责。
 */
@Slf4j
final class TaskExecutionRuntimeSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final IAgentTaskRepository agentTaskRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final TaskPersistenceApplicationService taskPersistenceApplicationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ExecutorService taskCallExecutor;
    private final ScheduledExecutorService heartbeatScheduler;
    private final int claimLeaseSeconds;
    private final int claimHeartbeatSeconds;
    private final int executionTimeoutMs;
    private final DistributionSummary executionRetrySummary;
    private final Counter heartbeatSuccessCounter;
    private final Counter heartbeatGuardRejectCounter;
    private final Counter heartbeatErrorCounter;
    private final Counter claimedUpdateSuccessCounter;
    private final Counter claimedUpdateGuardRejectCounter;
    private final Counter claimedUpdateErrorCounter;
    private final String claimOwner;
    private final boolean auditLogEnabled;
    private final boolean auditSuccessLogEnabled;

    TaskExecutionRuntimeSupport(IAgentTaskRepository agentTaskRepository,
                                PlanTaskEventPublisher planTaskEventPublisher,
                                TaskPersistenceApplicationService taskPersistenceApplicationService,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry,
                                ExecutorService taskCallExecutor,
                                ScheduledExecutorService heartbeatScheduler,
                                int claimLeaseSeconds,
                                int claimHeartbeatSeconds,
                                int executionTimeoutMs,
                                DistributionSummary executionRetrySummary,
                                Counter heartbeatSuccessCounter,
                                Counter heartbeatGuardRejectCounter,
                                Counter heartbeatErrorCounter,
                                Counter claimedUpdateSuccessCounter,
                                Counter claimedUpdateGuardRejectCounter,
                                Counter claimedUpdateErrorCounter,
                                String claimOwner,
                                boolean auditLogEnabled,
                                boolean auditSuccessLogEnabled) {
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.taskPersistenceApplicationService = taskPersistenceApplicationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.taskCallExecutor = taskCallExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.claimLeaseSeconds = claimLeaseSeconds;
        this.claimHeartbeatSeconds = claimHeartbeatSeconds;
        this.executionTimeoutMs = executionTimeoutMs;
        this.executionRetrySummary = executionRetrySummary;
        this.heartbeatSuccessCounter = heartbeatSuccessCounter;
        this.heartbeatGuardRejectCounter = heartbeatGuardRejectCounter;
        this.heartbeatErrorCounter = heartbeatErrorCounter;
        this.claimedUpdateSuccessCounter = claimedUpdateSuccessCounter;
        this.claimedUpdateGuardRejectCounter = claimedUpdateGuardRejectCounter;
        this.claimedUpdateErrorCounter = claimedUpdateErrorCounter;
        this.claimOwner = StringUtils.defaultIfBlank(claimOwner, "unknown");
        this.auditLogEnabled = auditLogEnabled;
        this.auditSuccessLogEnabled = auditSuccessLogEnabled;
    }

    ScheduledFuture<?> startHeartbeat(AgentTaskEntity task) {
        if (task == null || task.getId() == null || StringUtils.isBlank(task.getClaimOwner())
                || task.getExecutionAttempt() == null || claimHeartbeatSeconds <= 0) {
            return null;
        }
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                boolean renewed = agentTaskRepository.renewClaimLease(
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), claimLeaseSeconds);
                if (!renewed) {
                    heartbeatGuardRejectCounter.increment();
                    log.debug("Claim lease renew skipped. taskId={}, owner={}, attempt={}",
                            task.getId(), task.getClaimOwner(), task.getExecutionAttempt());
                    emitTaskAudit("lease_renew_guard_reject", task, "renewed=false");
                } else {
                    heartbeatSuccessCounter.increment();
                }
            } catch (Exception ex) {
                heartbeatErrorCounter.increment();
                log.warn("Failed to renew claim lease. taskId={}, owner={}, attempt={}, error={}",
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), ex.getMessage());
                emitTaskAudit("lease_renew_error", task, "error_type=" + classifyError(ex));
            }
        }, claimHeartbeatSeconds, claimHeartbeatSeconds, TimeUnit.SECONDS);
    }

    void stopHeartbeat(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    boolean releaseClaimForNonExecutablePlan(AgentTaskEntity task, AgentPlanEntity plan) {
        if (task == null) {
            return false;
        }
        try {
            task.rollbackToDispatchQueue();
            boolean updated = safeUpdateClaimedTask(task);
            if (updated) {
                emitTaskAudit("claimed_release_non_executable_plan", task,
                        "plan_status=" + (plan == null || plan.getStatus() == null ? "null" : plan.getStatus().name()));
                publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
            }
            return updated;
        } catch (Exception ex) {
            log.warn("Failed to release claimed task for non-executable plan. taskId={}, planId={}, error={}",
                    task.getId(), task.getPlanId(), ex.getMessage());
            emitTaskAudit("claimed_release_non_executable_plan_failed", task, "error_type=" + classifyError(ex));
            return false;
        }
    }

    void recordRetryDistribution(AgentTaskEntity task) {
        if (task == null) {
            return;
        }
        int retry = task.getCurrentRetry() == null ? 0 : Math.max(task.getCurrentRetry(), 0);
        executionRetrySummary.record(retry);
    }

    ChatResponse callTaskClientWithTimeout(ChatClient taskClient, String prompt) {
        Future<ChatResponse> future = taskCallExecutor.submit(() -> {
            ChatClient.CallResponseSpec callResponse = taskClient.prompt(prompt).call();
            return callResponse == null ? null : callResponse.chatResponse();
        });
        try {
            return future.get(executionTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TaskExecutionRunner.TaskCallTimeoutException("Task execution timed out after " + executionTimeoutMs + " ms", ex);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task execution interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause == null ? "Task execution failed" : cause.getMessage(), cause);
        }
    }

    void persistTimeoutExecution(TaskExecutionEntity execution,
                                 long startTime,
                                 TaskExecutionRunner.TaskCallTimeoutException timeoutException) {
        if (execution == null) {
            return;
        }
        execution.recordError(timeoutException.getMessage());
        execution.markAsInvalid(timeoutException.getMessage());
        execution.setErrorType("timeout");
        execution.setExecutionTime(startTime);
        safeSaveExecution(execution);
    }

    void recordTimeoutMetrics(AgentTaskEntity task, boolean retrying) {
        String taskType = resolveTaskType(task);
        meterRegistry.counter("agent.task.execution.timeout.total",
                "task_type", taskType).increment();
        if (retrying) {
            meterRegistry.counter("agent.task.execution.timeout.retry.total",
                    "task_type", taskType).increment();
        } else {
            meterRegistry.counter("agent.task.execution.timeout.final_fail.total",
                    "task_type", taskType).increment();
        }
    }

    boolean safeUpdateClaimedTask(AgentTaskEntity task) {
        TaskPersistenceApplicationService.ClaimedTaskUpdateResult result =
                taskPersistenceApplicationService.updateClaimedTask(task);

        if (result.outcome() == TaskPersistenceApplicationService.ClaimedTaskUpdateOutcome.UPDATED) {
            claimedUpdateSuccessCounter.increment();
            return true;
        }

        if (result.outcome() == TaskPersistenceApplicationService.ClaimedTaskUpdateOutcome.GUARD_REJECTED) {
            claimedUpdateGuardRejectCounter.increment();
            log.debug("Claimed task update skipped by ownership/attempt guard. taskId={}, owner={}, attempt={}, status={}",
                    task == null ? null : task.getId(),
                    task == null ? null : task.getClaimOwner(),
                    task == null ? null : task.getExecutionAttempt(),
                    task == null ? null : task.getStatus());
            emitTaskAudit("claimed_update_guard_reject", task,
                    "status=" + (task == null || task.getStatus() == null ? "null" : task.getStatus().name()));
            return false;
        }

        claimedUpdateErrorCounter.increment();
        log.warn("Failed to update claimed task. taskId={}, owner={}, attempt={}, error={}",
                task == null ? null : task.getId(),
                task == null ? null : task.getClaimOwner(),
                task == null ? null : task.getExecutionAttempt(),
                result.errorMessage());
        emitTaskAudit("claimed_update_error", task, "error_type=persistence_error");
        return false;
    }

    void safeSaveExecution(TaskExecutionEntity execution) {
        TaskPersistenceApplicationService.ExecutionSaveResult result =
                taskPersistenceApplicationService.saveExecution(execution);
        if (result.saved()) {
            return;
        }
        log.warn("Failed to save task execution. taskId={}, error={}",
                execution == null ? null : execution.getTaskId(),
                result.errorMessage());
    }

    Map<String, Object> buildTaskData(AgentTaskEntity task) {
        Map<String, Object> data = new HashMap<>();
        if (task == null) {
            return data;
        }
        data.put("planId", task.getPlanId());
        data.put("taskId", task.getId());
        data.put("nodeId", task.getNodeId());
        data.put("status", task.getStatus() == null ? null : task.getStatus().name());
        data.put("taskType", task.getTaskType() == null ? null : task.getTaskType().name());
        return data;
    }

    Map<String, Object> buildTaskLog(AgentTaskEntity task) {
        Map<String, Object> data = buildTaskData(task);
        data.put("output", task == null ? null : task.getOutputResult());
        return data;
    }

    void publishTaskStarted(AgentTaskEntity task) {
        publishTaskEvent(PlanTaskEventTypeEnum.TASK_STARTED, task, buildTaskData(task));
    }

    void auditClaimReclaimed(AgentTaskEntity task) {
        emitTaskAudit("claim_reclaimed", task, "lease_reclaimed=true");
    }

    void auditClaimAcquired(AgentTaskEntity task) {
        if (auditSuccessLogEnabled) {
            emitTaskAudit("claim_acquired", task, "lease_reclaimed=false");
        }
    }

    void auditDispatchSubmitted(AgentTaskEntity task) {
        if (auditSuccessLogEnabled) {
            emitTaskAudit("dispatch_submitted", task, "-");
        }
    }

    void auditDispatchRejected(AgentTaskEntity task) {
        emitTaskAudit("dispatch_rejected", task, "error_type=rejected");
    }

    void auditDispatchError(AgentTaskEntity task, Throwable throwable) {
        emitTaskAudit("dispatch_error", task, "error_type=" + classifyError(throwable));
    }

    void auditExecutionStarted(AgentTaskEntity task) {
        if (auditSuccessLogEnabled) {
            emitTaskAudit("execution_started", task, "-");
        }
    }

    void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data) {
        if (eventType == null || task == null || task.getPlanId() == null) {
            return;
        }
        try {
            planTaskEventPublisher.publish(eventType,
                    task.getPlanId(),
                    task.getId(),
                    data == null ? Collections.emptyMap() : data);
        } catch (Exception ex) {
            log.warn("Failed to publish task event. planId={}, taskId={}, type={}, error={}",
                    task.getPlanId(), task.getId(), eventType, ex.getMessage());
        }
    }

    void handleValidationFailure(AgentTaskEntity task, String feedback) {
        try {
            task.startRefining();
            safeUpdateClaimedTask(task);
        } catch (Exception ex) {
            String reason = StringUtils.isBlank(feedback) ? ex.getMessage() : feedback;
            task.fail("Validation failed: " + reason);
            safeUpdateClaimedTask(task);
        }
    }

    String classifyError(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String type = throwable.getClass().getSimpleName();
        String message = StringUtils.defaultString(throwable.getMessage()).toLowerCase();
        if (message.contains("optimistic lock")) {
            return "optimistic_lock";
        }
        if (message.contains("timeout")) {
            return "timeout";
        }
        if (message.contains("connection") || message.contains("jdbc") || message.contains("sql")) {
            return "db_error";
        }
        if (type.contains("Json") || message.contains("json")) {
            return "json_error";
        }
        return "runtime_error";
    }

    String normalizeExecutionResult(String outcome) {
        return StringUtils.defaultIfBlank(outcome, "unknown");
    }

    String normalizeFailureMetricErrorType(String errorType) {
        return StringUtils.defaultIfBlank(errorType, "unknown");
    }

    String normalizeAuditErrorType(String errorType) {
        return StringUtils.defaultIfBlank(errorType, "none");
    }

    boolean shouldEmitExecutionAudit(String result) {
        if (auditSuccessLogEnabled) {
            return true;
        }
        return "failed".equals(result)
                || "validation_rejected".equals(result)
                || "critic_rejected".equals(result)
                || "update_guard_reject".equals(result);
    }

    void auditExecutionResult(AgentTaskEntity task, String result, String errorType) {
        String normalizedResult = normalizeExecutionResult(result);
        if (!shouldEmitExecutionAudit(normalizedResult)) {
            return;
        }
        emitTaskAudit("execution_" + normalizedResult, task,
                "error_type=" + normalizeAuditErrorType(errorType));
    }

    String extractModelName(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        return chatResponse.getMetadata().getModel();
    }

    Map<String, Object> extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return null;
        }
        Map<String, Object> tokenUsage = new HashMap<>();
        if (usage.getPromptTokens() != null) {
            tokenUsage.put("prompt_tokens", usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            tokenUsage.put("completion_tokens", usage.getCompletionTokens());
        }
        if (usage.getTotalTokens() != null) {
            tokenUsage.put("total_tokens", usage.getTotalTokens());
        }
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage != null) {
            tokenUsage.put("native_usage", normalizeNativeUsage(nativeUsage));
        }
        return tokenUsage.isEmpty() ? null : tokenUsage;
    }

    String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        return StringUtils.defaultString(chatResponse.getResult().getOutput().getText());
    }

    private Object normalizeNativeUsage(Object nativeUsage) {
        if (nativeUsage == null) {
            return null;
        }
        if (nativeUsage instanceof Map<?, ?>) {
            return nativeUsage;
        }
        try {
            return objectMapper.convertValue(nativeUsage, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            return String.valueOf(nativeUsage);
        }
    }

    String resolveTaskType(AgentTaskEntity task) {
        if (task == null || task.getTaskType() == null) {
            return "unknown";
        }
        return task.getTaskType().name().toLowerCase();
    }

    private void emitTaskAudit(String event, AgentTaskEntity task, String detail) {
        if (!auditLogEnabled || StringUtils.isBlank(event)) {
            return;
        }
        String normalizedDetail = StringUtils.defaultIfBlank(detail, "-");
        if (task == null) {
            log.info("TASK_AUDIT event={}, taskId=null, planId=null, nodeId=null, owner={}, attempt=null, detail={}",
                    event, claimOwner, normalizedDetail);
            return;
        }
        log.info("TASK_AUDIT event={}, taskId={}, planId={}, nodeId={}, owner={}, attempt={}, detail={}",
                event,
                task.getId(),
                task.getPlanId(),
                task.getNodeId(),
                StringUtils.defaultIfBlank(task.getClaimOwner(), claimOwner),
                task.getExecutionAttempt(),
                normalizedDetail);
    }
}
