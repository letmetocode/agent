package com.getoffer.trigger.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskJsonDomainService;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 单任务执行中的流程支持组件：承载提示词构造、评估解析、回滚与黑板写回等逻辑。
 */
@Slf4j
final class TaskExecutionFlowSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final IAgentTaskRepository agentTaskRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final TaskPromptDomainService taskPromptDomainService;
    private final TaskEvaluationDomainService taskEvaluationDomainService;
    private final TaskRecoveryDomainService taskRecoveryDomainService;
    private final TaskBlackboardDomainService taskBlackboardDomainService;
    private final TaskJsonDomainService taskJsonDomainService;
    private final TaskPersistenceApplicationService taskPersistenceApplicationService;
    private final ObjectMapper objectMapper;
    private final int planContextUpdateMaxRetry;

    TaskExecutionFlowSupport(IAgentTaskRepository agentTaskRepository,
                             ITaskExecutionRepository taskExecutionRepository,
                             TaskPromptDomainService taskPromptDomainService,
                             TaskEvaluationDomainService taskEvaluationDomainService,
                             TaskRecoveryDomainService taskRecoveryDomainService,
                             TaskBlackboardDomainService taskBlackboardDomainService,
                             TaskJsonDomainService taskJsonDomainService,
                             TaskPersistenceApplicationService taskPersistenceApplicationService,
                             ObjectMapper objectMapper,
                             int planContextUpdateMaxRetry) {
        this.agentTaskRepository = agentTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.taskPromptDomainService = taskPromptDomainService;
        this.taskEvaluationDomainService = taskEvaluationDomainService;
        this.taskRecoveryDomainService = taskRecoveryDomainService;
        this.taskBlackboardDomainService = taskBlackboardDomainService;
        this.taskJsonDomainService = taskJsonDomainService;
        this.taskPersistenceApplicationService = taskPersistenceApplicationService;
        this.objectMapper = objectMapper;
        this.planContextUpdateMaxRetry = planContextUpdateMaxRetry;
    }

    String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        return taskPromptDomainService.buildWorkerPrompt(task, plan, this::serializeJsonForDomain);
    }

    String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        String targetNodeId = taskPromptDomainService.resolveTargetNodeId(task);
        AgentTaskEntity targetTask = targetNodeId == null ? null
                : agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        String targetOutput = targetTask == null ? "" : StringUtils.defaultString(targetTask.getOutputResult());
        return taskPromptDomainService.buildCriticPrompt(task, plan, targetNodeId, targetOutput, this::serializeJsonForDomain);
    }

    String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        TaskExecutionEntity lastExecution = loadLastExecution(task.getId());
        String lastResponse = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getLlmResponseRaw());
        String feedback = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getValidationFeedback());
        String basePrompt = buildPrompt(task, plan);
        return taskPromptDomainService.buildRefinePrompt(basePrompt, lastResponse, feedback);
    }

    String buildRetrySystemPrompt(AgentTaskEntity task) {
        return taskPromptDomainService.buildRetrySystemPrompt(task);
    }

    TaskExecutionRunner.CriticDecision parseCriticDecision(String response) {
        Map<String, Object> payload = taskJsonDomainService.parseEmbeddedJsonObject(
                response,
                this::parseJsonStrictObject
        );
        TaskEvaluationDomainService.CriticDecision decision = taskEvaluationDomainService.parseCriticDecision(response, payload);
        return new TaskExecutionRunner.CriticDecision(decision.pass(), decision.feedback());
    }

    TaskExecutionRunner.ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
        Map<String, Object> payload = taskJsonDomainService.parseEmbeddedJsonObject(
                response,
                this::parseJsonStrictObject
        );
        TaskEvaluationDomainService.ValidationResult result =
                taskEvaluationDomainService.evaluateValidation(task, response, payload);
        return new TaskExecutionRunner.ValidationResult(result.valid(), result.feedback());
    }

    void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback) {
        String targetNodeId = taskPromptDomainService.resolveTargetNodeId(criticTask);
        if (StringUtils.isBlank(targetNodeId)) {
            log.warn("Critic rollback skipped: target node not found. planId={}, taskId={}", plan.getId(), criticTask.getId());
            return;
        }

        AgentTaskEntity target = agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        TaskRecoveryDomainService.RecoveryDecision decision =
                taskRecoveryDomainService.applyCriticFeedback(target, feedback);

        if (decision == TaskRecoveryDomainService.RecoveryDecision.NOT_FOUND) {
            log.warn("Critic rollback skipped: target task not found. planId={}, nodeId={}", plan.getId(), targetNodeId);
            return;
        }
        if (decision == TaskRecoveryDomainService.RecoveryDecision.ALREADY_FAILED) {
            return;
        }
        if (decision.requiresUpdate()) {
            safeUpdateTask(target);
        }
    }

    void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output) {
        if (plan == null || plan.getId() == null || task == null) {
            return;
        }
        Map<String, Object> delta = taskBlackboardDomainService.buildContextDelta(
                task,
                output,
                text -> taskJsonDomainService.parseStrictJsonObject(text, this::parseJsonStrictObject)
        );

        TaskPersistenceApplicationService.PlanContextUpdateResult result =
                taskPersistenceApplicationService.updatePlanContextWithRetry(
                        plan.getId(),
                        delta,
                        planContextUpdateMaxRetry
                );

        if (result.outcome() == TaskPersistenceApplicationService.PlanContextUpdateOutcome.UPDATED) {
            plan.setGlobalContext(result.mergedContext());
            plan.setVersion(result.version());
            return;
        }

        if (result.outcome() == TaskPersistenceApplicationService.PlanContextUpdateOutcome.PLAN_NOT_FOUND) {
            log.warn("Failed to update plan context because plan not found. planId={}", plan.getId());
            return;
        }

        if (result.outcome() == TaskPersistenceApplicationService.PlanContextUpdateOutcome.OPTIMISTIC_LOCK_EXHAUSTED) {
            log.warn("Failed to update plan context after retries. planId={}, attempts={}, error={}",
                    plan.getId(),
                    result.attempt(),
                    result.errorMessage());
            return;
        }

        log.warn("Failed to update plan context. planId={}, error={}",
                plan.getId(),
                result.errorMessage());
    }

    private TaskExecutionEntity loadLastExecution(Long taskId) {
        if (taskId == null) {
            return null;
        }
        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);
        if (executions == null || executions.isEmpty()) {
            return null;
        }
        return executions.get(0);
    }

    private boolean safeUpdateTask(AgentTaskEntity task) {
        TaskPersistenceApplicationService.TaskUpdateResult result =
                taskPersistenceApplicationService.updateTask(task);
        if (result.updated()) {
            return true;
        }
        log.warn("Failed to update task status. taskId={}, error={}",
                task == null ? null : task.getId(),
                result.errorMessage());
        return false;
    }

    private Map<String, Object> parseJsonStrictObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String serializeJsonForDomain(Object value) {
        return taskJsonDomainService.toJson(value, this::serializeJsonStrict);
    }

    private String serializeJsonStrict(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
