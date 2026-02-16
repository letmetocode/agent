package com.getoffer.trigger.job;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * TaskExecutionRunner 执行支持适配器：协调 TaskExecutor 与执行支撑组件。
 */
final class TaskExecutionSupportAdapter implements TaskExecutionRunner.ExecutionSupport {

    private final TaskExecutionRuntimeSupport runtimeSupport;
    private final TaskDispatchDomainService taskDispatchDomainService;
    private final IAgentPlanRepository agentPlanRepository;
    private final TaskExecutionDomainService taskExecutionDomainService;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final TaskEvaluationDomainService taskEvaluationDomainService;
    private final int executionTimeoutRetryMax;
    private final TaskExecutionFlowSupport taskExecutionFlowSupport;
    private final TaskExecutionClientResolver taskExecutionClientResolver;

    TaskExecutionSupportAdapter(TaskExecutionRuntimeSupport runtimeSupport,
                                TaskDispatchDomainService taskDispatchDomainService,
                                IAgentPlanRepository agentPlanRepository,
                                TaskExecutionDomainService taskExecutionDomainService,
                                ITaskExecutionRepository taskExecutionRepository,
                                TaskEvaluationDomainService taskEvaluationDomainService,
                                int executionTimeoutRetryMax,
                                TaskExecutionFlowSupport taskExecutionFlowSupport,
                                TaskExecutionClientResolver taskExecutionClientResolver) {
        this.runtimeSupport = runtimeSupport;
        this.taskDispatchDomainService = taskDispatchDomainService;
        this.agentPlanRepository = agentPlanRepository;
        this.taskExecutionDomainService = taskExecutionDomainService;
        this.taskExecutionRepository = taskExecutionRepository;
        this.taskEvaluationDomainService = taskEvaluationDomainService;
        this.executionTimeoutRetryMax = executionTimeoutRetryMax;
        this.taskExecutionFlowSupport = taskExecutionFlowSupport;
        this.taskExecutionClientResolver = taskExecutionClientResolver;
    }

    @Override
    public boolean hasValidClaim(AgentTaskEntity task) {
        return taskDispatchDomainService.hasValidClaim(task);
    }

    @Override
    public ScheduledFuture<?> startHeartbeat(AgentTaskEntity task) {
        return runtimeSupport.startHeartbeat(task);
    }

    @Override
    public void stopHeartbeat(ScheduledFuture<?> future) {
        runtimeSupport.stopHeartbeat(future);
    }

    @Override
    public AgentPlanEntity findPlan(Long planId) {
        return agentPlanRepository.findById(planId);
    }

    @Override
    public boolean releaseClaimForNonExecutablePlan(AgentTaskEntity task, AgentPlanEntity plan) {
        return runtimeSupport.releaseClaimForNonExecutablePlan(task, plan);
    }

    @Override
    public void recordRetryDistribution(AgentTaskEntity task) {
        runtimeSupport.recordRetryDistribution(task);
    }

    @Override
    public int resolveAttemptNumber(AgentTaskEntity task) {
        return taskExecutionDomainService.resolveAttemptNumber(
                taskExecutionRepository.getMaxAttemptNumber(task.getId()),
                task
        );
    }

    @Override
    public boolean isCriticTask(AgentTaskEntity task) {
        return task != null && task.getTaskType() == TaskTypeEnum.CRITIC;
    }

    @Override
    public String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        return taskExecutionFlowSupport.buildCriticPrompt(task, plan);
    }

    @Override
    public String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        return taskExecutionFlowSupport.buildRefinePrompt(task, plan);
    }

    @Override
    public String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        return taskExecutionFlowSupport.buildPrompt(task, plan);
    }

    @Override
    public String buildRetrySystemPrompt(AgentTaskEntity task) {
        return taskExecutionFlowSupport.buildRetrySystemPrompt(task);
    }

    @Override
    public ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix) {
        return taskExecutionClientResolver.resolveTaskClient(task, plan, systemPromptSuffix);
    }

    @Override
    public ChatResponse callTaskClientWithTimeout(ChatClient taskClient, String prompt) {
        return runtimeSupport.callTaskClientWithTimeout(taskClient, prompt);
    }

    @Override
    public void persistTimeoutExecution(TaskExecutionEntity execution,
                                        long startTime,
                                        TaskExecutionRunner.TaskCallTimeoutException timeoutException) {
        runtimeSupport.persistTimeoutExecution(execution, startTime, timeoutException);
    }

    @Override
    public boolean canTimeoutRetry(AgentTaskEntity task, int timeoutRetryCount) {
        return taskExecutionDomainService.canTimeoutRetry(task, timeoutRetryCount, executionTimeoutRetryMax);
    }

    @Override
    public void recordTimeoutMetrics(AgentTaskEntity task, boolean retrying) {
        runtimeSupport.recordTimeoutMetrics(task, retrying);
    }

    @Override
    public void applyTimeoutRetry(AgentTaskEntity task, String errorMessage) {
        taskExecutionDomainService.applyTimeoutRetry(task, errorMessage);
    }

    @Override
    public boolean safeUpdateClaimedTask(AgentTaskEntity task) {
        return runtimeSupport.safeUpdateClaimedTask(task);
    }

    @Override
    public void safeSaveExecution(TaskExecutionEntity execution) {
        runtimeSupport.safeSaveExecution(execution);
    }

    @Override
    public Map<String, Object> buildTaskData(AgentTaskEntity task) {
        return runtimeSupport.buildTaskData(task);
    }

    @Override
    public Map<String, Object> buildTaskLog(AgentTaskEntity task) {
        return runtimeSupport.buildTaskLog(task);
    }

    @Override
    public void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data) {
        runtimeSupport.publishTaskEvent(eventType, task, data);
    }

    @Override
    public TaskExecutionRunner.CriticDecision parseCriticDecision(String response) {
        return taskExecutionFlowSupport.parseCriticDecision(response);
    }

    @Override
    public void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback) {
        taskExecutionFlowSupport.rollbackTarget(plan, criticTask, feedback);
    }

    @Override
    public boolean needsValidation(AgentTaskEntity task) {
        return taskEvaluationDomainService.needsValidation(task);
    }

    @Override
    public TaskExecutionRunner.ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
        return taskExecutionFlowSupport.evaluateValidation(task, response);
    }

    @Override
    public void handleValidationFailure(AgentTaskEntity task, String feedback) {
        runtimeSupport.handleValidationFailure(task, feedback);
    }

    @Override
    public void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output) {
        taskExecutionFlowSupport.syncBlackboard(plan, task, output);
    }

    @Override
    public String classifyError(Throwable throwable) {
        return runtimeSupport.classifyError(throwable);
    }

    @Override
    public String extractContent(ChatResponse chatResponse) {
        return runtimeSupport.extractContent(chatResponse);
    }

    @Override
    public String extractModelName(ChatResponse chatResponse) {
        return runtimeSupport.extractModelName(chatResponse);
    }

    @Override
    public Map<String, Object> extractTokenUsage(ChatResponse chatResponse) {
        return runtimeSupport.extractTokenUsage(chatResponse);
    }
}
