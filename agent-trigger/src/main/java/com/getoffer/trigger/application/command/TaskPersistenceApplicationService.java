package com.getoffer.trigger.application.command;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IQualityEvaluationEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.QualityEvaluationEventEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskPersistencePolicyDomainService;
import com.getoffer.types.enums.TaskTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task 持久化写用例：统一承载持久化失败处理与重试策略。
 */
@Service
@Slf4j
public class TaskPersistenceApplicationService {

    private static final Pattern SCORE_PATTERN = Pattern.compile("score\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    private final IAgentTaskRepository agentTaskRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final TaskBlackboardDomainService taskBlackboardDomainService;
    private final TaskPersistencePolicyDomainService taskPersistencePolicyDomainService;
    private final IQualityEvaluationEventRepository qualityEvaluationEventRepository;

    public TaskPersistenceApplicationService(IAgentTaskRepository agentTaskRepository,
                                             ITaskExecutionRepository taskExecutionRepository,
                                             IAgentPlanRepository agentPlanRepository,
                                             TaskBlackboardDomainService taskBlackboardDomainService,
                                             TaskPersistencePolicyDomainService taskPersistencePolicyDomainService) {
        this(agentTaskRepository,
                taskExecutionRepository,
                agentPlanRepository,
                taskBlackboardDomainService,
                taskPersistencePolicyDomainService,
                null);
    }

    @Autowired
    public TaskPersistenceApplicationService(IAgentTaskRepository agentTaskRepository,
                                             ITaskExecutionRepository taskExecutionRepository,
                                             IAgentPlanRepository agentPlanRepository,
                                             TaskBlackboardDomainService taskBlackboardDomainService,
                                             TaskPersistencePolicyDomainService taskPersistencePolicyDomainService,
                                             @Autowired(required = false) IQualityEvaluationEventRepository qualityEvaluationEventRepository) {
        this.agentTaskRepository = agentTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.taskBlackboardDomainService = taskBlackboardDomainService;
        this.taskPersistencePolicyDomainService = taskPersistencePolicyDomainService;
        this.qualityEvaluationEventRepository = qualityEvaluationEventRepository;
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
            persistQualityEvaluationEvent(execution);
            return ExecutionSaveResult.success();
        } catch (Exception ex) {
            return ExecutionSaveResult.error(taskPersistencePolicyDomainService.normalizeErrorMessage(ex));
        }
    }

    private void persistQualityEvaluationEvent(TaskExecutionEntity execution) {
        if (qualityEvaluationEventRepository == null || execution == null || execution.getTaskId() == null) {
            return;
        }
        if (execution.getIsValid() == null && isBlank(execution.getValidationFeedback())) {
            return;
        }
        try {
            AgentTaskEntity task = agentTaskRepository.findById(execution.getTaskId());
            if (task == null || task.getPlanId() == null) {
                return;
            }
            Map<String, Object> config = task.getConfigSnapshot() == null ? Collections.emptyMap() : task.getConfigSnapshot();
            String experimentKey = resolveExperimentKey(config);
            Map<String, Object> payload = new HashMap<>();
            String variant = resolveExperimentVariant(task, config, experimentKey, payload);

            payload.put("attemptNumber", execution.getAttemptNumber());
            payload.put("taskStatus", task.getStatus() == null ? null : task.getStatus().name());
            payload.put("taskType", task.getTaskType() == null ? null : task.getTaskType().name());
            payload.put("errorType", execution.getErrorType());

            QualityEvaluationEventEntity event = new QualityEvaluationEventEntity();
            event.setPlanId(task.getPlanId());
            event.setTaskId(task.getId());
            event.setExecutionId(execution.getId());
            event.setEvaluatorType(resolveEvaluatorType(task));
            event.setExperimentKey(experimentKey);
            event.setExperimentVariant(variant);
            event.setSchemaVersion(resolveSchemaVersion(config));
            event.setScore(resolveScore(execution));
            event.setPass(execution.getIsValid());
            event.setFeedback(execution.getValidationFeedback());
            event.setPayload(payload);
            qualityEvaluationEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Persist quality evaluation event failed. taskId={}, error={}",
                    execution.getTaskId(), ex.getMessage());
        }
    }

    private String resolveEvaluatorType(AgentTaskEntity task) {
        if (task != null && task.getTaskType() == TaskTypeEnum.CRITIC) {
            return "CRITIC";
        }
        return "WORKER_VALIDATOR";
    }

    private String resolveExperimentKey(Map<String, Object> config) {
        String explicit = getString(config, "qualityExperimentKey", "quality_experiment_key", "experimentKey", "experiment_key");
        return isBlank(explicit) ? "quality-validation-default" : explicit;
    }

    private String resolveExperimentVariant(AgentTaskEntity task,
                                            Map<String, Object> config,
                                            String experimentKey,
                                            Map<String, Object> payload) {
        String explicit = getString(config, "qualityExperimentVariant", "quality_experiment_variant", "experimentVariant", "experiment_variant");
        if (!isBlank(explicit)) {
            return explicit;
        }
        Boolean enabled = getBoolean(config, "qualityExperimentEnabled", "quality_experiment_enabled");
        if (enabled != null && !enabled) {
            payload.put("experimentEnabled", false);
            return "CONTROL";
        }
        double rolloutPercent = resolveRolloutPercent(config);
        int bucket = Math.floorMod((String.valueOf(task.getPlanId()) + ":" + task.getId() + ":" + experimentKey).hashCode(), 100);
        payload.put("bucket", bucket);
        payload.put("rolloutPercent", rolloutPercent);
        return bucket < rolloutPercent ? "B" : "A";
    }

    private double resolveRolloutPercent(Map<String, Object> config) {
        Double configured = getDouble(config, "qualityExperimentRolloutPercent", "quality_experiment_rollout_percent", "rolloutPercent");
        if (configured == null) {
            return 50.0D;
        }
        if (configured < 0) {
            return 0D;
        }
        if (configured > 100D) {
            return 100D;
        }
        return configured;
    }

    private String resolveSchemaVersion(Map<String, Object> config) {
        Map<String, Object> schema = getMap(config, "validationSchema", "validation_schema");
        String schemaVersion = getString(schema, "version", "schemaVersion", "schema_version");
        if (!isBlank(schemaVersion)) {
            return schemaVersion;
        }
        String fallback = getString(config, "validationSchemaVersion", "validation_schema_version");
        return isBlank(fallback) ? "v1" : fallback;
    }

    private Double resolveScore(TaskExecutionEntity execution) {
        if (execution == null) {
            return null;
        }
        String feedback = execution.getValidationFeedback();
        if (isBlank(feedback)) {
            return null;
        }
        Matcher matcher = SCORE_PATTERN.matcher(feedback);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (Exception ignore) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return Collections.emptyMap();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> mapValue) {
                return (Map<String, Object>) mapValue;
            }
        }
        return Collections.emptyMap();
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(value).trim().toLowerCase();
            if ("true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "0".equals(text) || "no".equals(text) || "n".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private Double getDouble(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
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
