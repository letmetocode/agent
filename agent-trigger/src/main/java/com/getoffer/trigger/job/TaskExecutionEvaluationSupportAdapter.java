package com.getoffer.trigger.job;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;

/**
 * TaskExecutionRunner 评估域支持适配器。
 */
final class TaskExecutionEvaluationSupportAdapter implements TaskExecutionRunner.EvaluationSupport {

    private final TaskEvaluationDomainService taskEvaluationDomainService;
    private final TaskExecutionFlowSupport taskExecutionFlowSupport;
    private final TaskExecutionRuntimeSupport runtimeSupport;

    TaskExecutionEvaluationSupportAdapter(TaskEvaluationDomainService taskEvaluationDomainService,
                                          TaskExecutionFlowSupport taskExecutionFlowSupport,
                                          TaskExecutionRuntimeSupport runtimeSupport) {
        this.taskEvaluationDomainService = taskEvaluationDomainService;
        this.taskExecutionFlowSupport = taskExecutionFlowSupport;
        this.runtimeSupport = runtimeSupport;
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
}
