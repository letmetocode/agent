package com.getoffer.trigger.job;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * 单任务执行运行器：负责执行主流程，TaskExecutor 负责调度与并发协调。
 */
@Slf4j
public class TaskExecutionRunner {

    public ExecutionResult run(AgentTaskEntity task, ExecutionSupport support) {
        return run(task, support, support, support);
    }

    public ExecutionResult run(AgentTaskEntity task,
                               CallSupport callSupport,
                               EvaluationSupport evaluationSupport,
                               PersistenceSupport persistenceSupport) {
        String outcome = "unknown";
        String errorType = "none";
        ScheduledFuture<?> heartbeatFuture = null;
        TaskExecutionEntity execution = null;

        try {
            if (task == null) {
                outcome = "skip_null_task";
                return new ExecutionResult(outcome, errorType);
            }
            if (!callSupport.hasValidClaim(task)) {
                outcome = "skip_invalid_claim";
                log.warn("Skip claimed task execution because claim metadata missing. taskId={}, claimOwner={}, attempt={}",
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt());
                return new ExecutionResult(outcome, errorType);
            }

            AgentPlanEntity plan = callSupport.findPlan(task.getPlanId());
            if (plan == null) {
                outcome = "skip_plan_not_found";
                log.warn("Skip task execution because plan not found. taskId={}, planId={}", task.getId(), task.getPlanId());
                return new ExecutionResult(outcome, errorType);
            }
            if (!plan.isExecutable()) {
                outcome = callSupport.releaseClaimForNonExecutablePlan(task, plan)
                        ? "skip_plan_not_executable_released"
                        : "skip_plan_not_executable_release_failed";
                log.debug("Skip task execution because plan is not executable. planId={}, status={}, taskId={}",
                        plan.getId(), plan.getStatus(), task.getId());
                return new ExecutionResult(outcome, errorType);
            }

            callSupport.recordRetryDistribution(task);
            boolean criticTask = callSupport.isCriticTask(task);
            boolean refining = task.getCurrentRetry() != null && task.getCurrentRetry() > 0;
            heartbeatFuture = callSupport.startHeartbeat(task);

            ChatResponse chatResponse;
            String response;
            int timeoutRetryCount = 0;
            while (true) {
                long startTime = System.currentTimeMillis();
                String prompt = criticTask ? callSupport.buildCriticPrompt(task, plan)
                        : (refining ? callSupport.buildRefinePrompt(task, plan) : callSupport.buildPrompt(task, plan));
                execution = new TaskExecutionEntity();
                execution.setTaskId(task.getId());
                execution.setAttemptNumber(callSupport.resolveAttemptNumber(task));
                execution.setPromptSnapshot(prompt);

                String systemPromptSuffix = callSupport.buildRetrySystemPrompt(task);
                ChatClient taskClient = callSupport.resolveTaskClient(task, plan, systemPromptSuffix);
                try {
                    chatResponse = callSupport.callTaskClientWithTimeout(taskClient, prompt);
                } catch (TaskCallTimeoutException timeoutException) {
                    callSupport.persistTimeoutExecution(execution, startTime, timeoutException);
                    boolean retrying = callSupport.canTimeoutRetry(task, timeoutRetryCount);
                    callSupport.recordTimeoutMetrics(task, retrying);
                    if (retrying) {
                        timeoutRetryCount++;
                        callSupport.applyTimeoutRetry(task, timeoutException.getMessage());
                        refining = true;
                        outcome = "timeout_retrying";
                        errorType = "timeout";
                        continue;
                    }
                    errorType = "timeout";
                    outcome = "failed";
                    task.fail(timeoutException.getMessage());
                    if (persistenceSupport.safeUpdateClaimedTask(task)) {
                        persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, persistenceSupport.buildTaskData(task));
                        persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                    }
                    log.warn("Task execution timed out and exhausted retries. taskId={}, nodeId={}",
                            task.getId(), task.getNodeId());
                    return new ExecutionResult(outcome, errorType);
                }

                response = callSupport.extractContent(chatResponse);
                execution.setModelName(callSupport.extractModelName(chatResponse));
                execution.setTokenUsage(callSupport.extractTokenUsage(chatResponse));
                execution.setLlmResponseRaw(response);
                execution.setExecutionTime(startTime);
                break;
            }

            if (criticTask) {
                CriticDecision decision = evaluationSupport.parseCriticDecision(response);
                if (decision.pass()) {
                    execution.markAsValid(decision.feedback());
                } else {
                    execution.markAsInvalid(decision.feedback());
                }
                persistenceSupport.safeSaveExecution(execution);

                task.startValidation();
                if (decision.pass()) {
                    task.complete(response);
                    boolean updated = persistenceSupport.safeUpdateClaimedTask(task);
                    outcome = updated ? "completed" : "update_guard_reject";
                    if (updated) {
                        persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, persistenceSupport.buildTaskData(task));
                        persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                    }
                } else {
                    task.setOutputResult(response);
                    task.resetToPending();
                    boolean updated = persistenceSupport.safeUpdateClaimedTask(task);
                    outcome = updated ? "critic_rejected" : "update_guard_reject";
                    if (updated) {
                        persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                    }
                    evaluationSupport.rollbackTarget(plan, task, decision.feedback());
                }
                return new ExecutionResult(outcome, errorType);
            }

            if (evaluationSupport.needsValidation(task)) {
                ValidationResult validation = evaluationSupport.evaluateValidation(task, response);
                if (validation.valid()) {
                    execution.markAsValid(validation.feedback());
                } else {
                    execution.markAsInvalid(validation.feedback());
                }
                persistenceSupport.safeSaveExecution(execution);

                task.startValidation();
                if (!validation.valid()) {
                    evaluationSupport.handleValidationFailure(task, validation.feedback());
                    outcome = "validation_rejected";
                    return new ExecutionResult(outcome, errorType);
                }
                task.complete(response);
                if (persistenceSupport.safeUpdateClaimedTask(task)) {
                    evaluationSupport.syncBlackboard(plan, task, response);
                    outcome = "completed";
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, persistenceSupport.buildTaskData(task));
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                } else {
                    outcome = "update_guard_reject";
                }
            } else {
                execution.markAsValid("no validator");
                persistenceSupport.safeSaveExecution(execution);

                task.startValidation();
                task.complete(response);
                if (persistenceSupport.safeUpdateClaimedTask(task)) {
                    evaluationSupport.syncBlackboard(plan, task, response);
                    outcome = "completed";
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, persistenceSupport.buildTaskData(task));
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                } else {
                    outcome = "update_guard_reject";
                }
            }
        } catch (Exception ex) {
            errorType = callSupport.classifyError(ex);
            outcome = "failed";
            if (execution == null) {
                execution = new TaskExecutionEntity();
                execution.setTaskId(task == null ? null : task.getId());
            }
            execution.recordError(ex.getMessage());
            execution.setErrorType(errorType);
            execution.setExecutionTime(System.currentTimeMillis());
            persistenceSupport.safeSaveExecution(execution);

            if (task != null) {
                task.fail(ex.getMessage());
                if (persistenceSupport.safeUpdateClaimedTask(task)) {
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, persistenceSupport.buildTaskData(task));
                    persistenceSupport.publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, persistenceSupport.buildTaskLog(task));
                }
                log.warn("Task execution failed. taskId={}, nodeId={}, error={}",
                        task.getId(), task.getNodeId(), ex.getMessage());
            } else {
                log.warn("Task execution failed before task initialization. error={}", ex.getMessage());
            }
        } finally {
            callSupport.stopHeartbeat(heartbeatFuture);
        }

        return new ExecutionResult(outcome, errorType);
    }

    public interface ExecutionSupport extends CallSupport, EvaluationSupport, PersistenceSupport {
    }

    public interface CallSupport extends ClaimSupport,
            PromptAndClientSupport,
            TimeoutSupport,
            ResponseSupport,
            ErrorSupport {
    }

    public interface ClaimSupport {
        boolean hasValidClaim(AgentTaskEntity task);

        ScheduledFuture<?> startHeartbeat(AgentTaskEntity task);

        void stopHeartbeat(ScheduledFuture<?> future);

        AgentPlanEntity findPlan(Long planId);

        boolean releaseClaimForNonExecutablePlan(AgentTaskEntity task, AgentPlanEntity plan);

        void recordRetryDistribution(AgentTaskEntity task);

        int resolveAttemptNumber(AgentTaskEntity task);
    }

    public interface PromptAndClientSupport {

        boolean isCriticTask(AgentTaskEntity task);

        String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan);

        String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan);

        String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan);

        String buildRetrySystemPrompt(AgentTaskEntity task);

        ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix);

        ChatResponse callTaskClientWithTimeout(ChatClient taskClient, String prompt);
    }

    public interface TimeoutSupport {

        void persistTimeoutExecution(TaskExecutionEntity execution, long startTime, TaskCallTimeoutException timeoutException);

        boolean canTimeoutRetry(AgentTaskEntity task, int timeoutRetryCount);

        void recordTimeoutMetrics(AgentTaskEntity task, boolean retrying);

        void applyTimeoutRetry(AgentTaskEntity task, String errorMessage);
    }

    public interface PersistenceAndEventSupport {
        boolean safeUpdateClaimedTask(AgentTaskEntity task);

        void safeSaveExecution(TaskExecutionEntity execution);

        Map<String, Object> buildTaskData(AgentTaskEntity task);

        Map<String, Object> buildTaskLog(AgentTaskEntity task);

        void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data);
    }

    public interface EvaluationFlowSupport {

        CriticDecision parseCriticDecision(String response);

        void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback);

        boolean needsValidation(AgentTaskEntity task);

        ValidationResult evaluateValidation(AgentTaskEntity task, String response);

        void handleValidationFailure(AgentTaskEntity task, String feedback);

        void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output);
    }

    public interface EvaluationSupport extends EvaluationFlowSupport {
    }

    public interface PersistenceSupport extends PersistenceAndEventSupport {
    }

    public interface ErrorSupport {
        String classifyError(Throwable throwable);
    }

    public interface ResponseSupport {
        String extractContent(ChatResponse chatResponse);

        String extractModelName(ChatResponse chatResponse);

        Map<String, Object> extractTokenUsage(ChatResponse chatResponse);
    }

    public record ExecutionResult(String outcome, String errorType) {
    }

    public record ValidationResult(boolean valid, String feedback) {
    }

    public record CriticDecision(boolean pass, String feedback) {
    }

    public static final class TaskCallTimeoutException extends RuntimeException {
        public TaskCallTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
