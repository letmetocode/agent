package com.getoffer.trigger.application.command;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskPersistencePolicyDomainService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Task 持久化写用例：统一承载持久化失败处理与重试策略。
 */
@Service
public class TaskPersistenceApplicationService {

    private final IAgentTaskRepository agentTaskRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final TaskBlackboardDomainService taskBlackboardDomainService;
    private final TaskPersistencePolicyDomainService taskPersistencePolicyDomainService;

    public TaskPersistenceApplicationService(IAgentTaskRepository agentTaskRepository,
                                             ITaskExecutionRepository taskExecutionRepository,
                                             IAgentPlanRepository agentPlanRepository,
                                             TaskBlackboardDomainService taskBlackboardDomainService,
                                             TaskPersistencePolicyDomainService taskPersistencePolicyDomainService) {
        this.agentTaskRepository = agentTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.taskBlackboardDomainService = taskBlackboardDomainService;
        this.taskPersistencePolicyDomainService = taskPersistencePolicyDomainService;
    }

    public TaskUpdateResult updateTask(AgentTaskEntity task) {
        if (task == null) {
            return TaskUpdateResult.error("task is null");
        }
        try {
            agentTaskRepository.update(task);
            return TaskUpdateResult.success();
        } catch (Exception ex) {
            return TaskUpdateResult.error(taskPersistencePolicyDomainService.normalizeErrorMessage(ex));
        }
    }

    public ClaimedTaskUpdateResult updateClaimedTask(AgentTaskEntity task) {
        if (task == null) {
            return ClaimedTaskUpdateResult.error("task is null");
        }
        try {
            boolean updated = agentTaskRepository.updateClaimedTaskState(task);
            if (updated) {
                return ClaimedTaskUpdateResult.updated();
            }
            return ClaimedTaskUpdateResult.guardRejected();
        } catch (Exception ex) {
            return ClaimedTaskUpdateResult.error(taskPersistencePolicyDomainService.normalizeErrorMessage(ex));
        }
    }

    public ExecutionSaveResult saveExecution(TaskExecutionEntity execution) {
        if (execution == null) {
            return ExecutionSaveResult.error("execution is null");
        }
        try {
            taskExecutionRepository.save(execution);
            return ExecutionSaveResult.success();
        } catch (Exception ex) {
            return ExecutionSaveResult.error(taskPersistencePolicyDomainService.normalizeErrorMessage(ex));
        }
    }

    public PlanContextUpdateResult updatePlanContextWithRetry(Long planId,
                                                              Map<String, Object> delta,
                                                              int maxAttempts) {
        if (planId == null) {
            return PlanContextUpdateResult.invalid("planId is null");
        }

        int retryLimit = Math.max(maxAttempts, 1);
        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            AgentPlanEntity latestPlan = agentPlanRepository.findById(planId);
            if (latestPlan == null) {
                return PlanContextUpdateResult.planNotFound(attempt);
            }

            Map<String, Object> mergedContext = taskBlackboardDomainService.mergeContext(latestPlan.getGlobalContext(), delta);
            latestPlan.setGlobalContext(mergedContext);

            try {
                agentPlanRepository.update(latestPlan);
                return PlanContextUpdateResult.updated(mergedContext, latestPlan.getVersion(), attempt);
            } catch (Exception ex) {
                String errorMessage = taskPersistencePolicyDomainService.normalizeErrorMessage(ex);
                boolean optimisticLock = taskPersistencePolicyDomainService.isOptimisticLockConflict(errorMessage);
                if (optimisticLock && taskPersistencePolicyDomainService.shouldRetryOptimisticLock(attempt, retryLimit)) {
                    continue;
                }
                if (optimisticLock) {
                    return PlanContextUpdateResult.optimisticLockExhausted(attempt, errorMessage);
                }
                return PlanContextUpdateResult.error(attempt, errorMessage);
            }
        }

        return PlanContextUpdateResult.optimisticLockExhausted(retryLimit, "Optimistic lock retry exhausted");
    }

    public record TaskUpdateResult(boolean updated, String errorMessage) {
        public static TaskUpdateResult success() {
            return new TaskUpdateResult(true, null);
        }

        public static TaskUpdateResult error(String errorMessage) {
            return new TaskUpdateResult(false, errorMessage);
        }
    }

    public enum ClaimedTaskUpdateOutcome {
        UPDATED,
        GUARD_REJECTED,
        ERROR
    }

    public record ClaimedTaskUpdateResult(ClaimedTaskUpdateOutcome outcome, String errorMessage) {
        public static ClaimedTaskUpdateResult updated() {
            return new ClaimedTaskUpdateResult(ClaimedTaskUpdateOutcome.UPDATED, null);
        }

        public static ClaimedTaskUpdateResult guardRejected() {
            return new ClaimedTaskUpdateResult(ClaimedTaskUpdateOutcome.GUARD_REJECTED, null);
        }

        public static ClaimedTaskUpdateResult error(String errorMessage) {
            return new ClaimedTaskUpdateResult(ClaimedTaskUpdateOutcome.ERROR, errorMessage);
        }
    }

    public record ExecutionSaveResult(boolean saved, String errorMessage) {
        public static ExecutionSaveResult success() {
            return new ExecutionSaveResult(true, null);
        }

        public static ExecutionSaveResult error(String errorMessage) {
            return new ExecutionSaveResult(false, errorMessage);
        }
    }

    public enum PlanContextUpdateOutcome {
        UPDATED,
        PLAN_NOT_FOUND,
        INVALID_INPUT,
        OPTIMISTIC_LOCK_EXHAUSTED,
        ERROR
    }

    public record PlanContextUpdateResult(PlanContextUpdateOutcome outcome,
                                          Map<String, Object> mergedContext,
                                          Integer version,
                                          int attempt,
                                          String errorMessage) {
        public static PlanContextUpdateResult updated(Map<String, Object> mergedContext,
                                                      Integer version,
                                                      int attempt) {
            return new PlanContextUpdateResult(PlanContextUpdateOutcome.UPDATED,
                    mergedContext,
                    version,
                    attempt,
                    null);
        }

        public static PlanContextUpdateResult planNotFound(int attempt) {
            return new PlanContextUpdateResult(PlanContextUpdateOutcome.PLAN_NOT_FOUND,
                    null,
                    null,
                    attempt,
                    "plan not found");
        }

        public static PlanContextUpdateResult invalid(String errorMessage) {
            return new PlanContextUpdateResult(PlanContextUpdateOutcome.INVALID_INPUT,
                    null,
                    null,
                    0,
                    errorMessage);
        }

        public static PlanContextUpdateResult optimisticLockExhausted(int attempt, String errorMessage) {
            return new PlanContextUpdateResult(PlanContextUpdateOutcome.OPTIMISTIC_LOCK_EXHAUSTED,
                    null,
                    null,
                    attempt,
                    errorMessage);
        }

        public static PlanContextUpdateResult error(int attempt, String errorMessage) {
            return new PlanContextUpdateResult(PlanContextUpdateOutcome.ERROR,
                    null,
                    null,
                    attempt,
                    errorMessage);
        }
    }
}
