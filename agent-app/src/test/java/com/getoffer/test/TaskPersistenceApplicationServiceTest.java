package com.getoffer.test;

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
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskPersistenceApplicationServiceTest {

    private final IAgentTaskRepository taskRepository = mock(IAgentTaskRepository.class);
    private final ITaskExecutionRepository executionRepository = mock(ITaskExecutionRepository.class);
    private final IAgentPlanRepository planRepository = mock(IAgentPlanRepository.class);
    private final IQualityEvaluationEventRepository qualityEventRepository = mock(IQualityEvaluationEventRepository.class);

    private final TaskPersistenceApplicationService service = new TaskPersistenceApplicationService(
            taskRepository,
            executionRepository,
            planRepository,
            new TaskBlackboardDomainService(),
            new TaskPersistencePolicyDomainService()
    );

    private final TaskPersistenceApplicationService qualityAwareService = new TaskPersistenceApplicationService(
            taskRepository,
            executionRepository,
            planRepository,
            new TaskBlackboardDomainService(),
            new TaskPersistencePolicyDomainService(),
            qualityEventRepository
    );

    @Test
    public void shouldReturnGuardRejectedWhenClaimedTaskUpdateAffectedZeroRows() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(101L);

        when(taskRepository.updateClaimedTaskState(task)).thenReturn(false);

        TaskPersistenceApplicationService.ClaimedTaskUpdateResult result = service.updateClaimedTask(task);

        Assertions.assertEquals(TaskPersistenceApplicationService.ClaimedTaskUpdateOutcome.GUARD_REJECTED, result.outcome());
        Assertions.assertNull(result.errorMessage());
    }

    @Test
    public void shouldRetryPlanContextUpdateAfterOptimisticLock() {
        AgentPlanEntity first = buildPlan(1L, 3, mapOf("a", 1));
        AgentPlanEntity second = buildPlan(1L, 4, mapOf("a", 1));

        when(planRepository.findById(1L)).thenReturn(first, second);
        doThrow(new RuntimeException("Optimistic lock conflict"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(planRepository).update(any(AgentPlanEntity.class));

        TaskPersistenceApplicationService.PlanContextUpdateResult result =
                service.updatePlanContextWithRetry(1L, mapOf("b", 2), 3);

        Assertions.assertEquals(TaskPersistenceApplicationService.PlanContextUpdateOutcome.UPDATED, result.outcome());
        Assertions.assertEquals(2, result.attempt());
        Assertions.assertEquals(1, result.mergedContext().get("a"));
        Assertions.assertEquals(2, result.mergedContext().get("b"));
        verify(planRepository, times(2)).findById(1L);
        verify(planRepository, times(2)).update(any(AgentPlanEntity.class));
    }

    @Test
    public void shouldReturnOptimisticLockExhaustedWhenRetryExceeded() {
        when(planRepository.findById(9L)).thenAnswer(invocation -> buildPlan(9L, 1, mapOf("x", "y")));
        doThrow(new RuntimeException("Optimistic lock conflict")).when(planRepository).update(any(AgentPlanEntity.class));

        TaskPersistenceApplicationService.PlanContextUpdateResult result =
                service.updatePlanContextWithRetry(9L, mapOf("k", "v"), 2);

        Assertions.assertEquals(TaskPersistenceApplicationService.PlanContextUpdateOutcome.OPTIMISTIC_LOCK_EXHAUSTED,
                result.outcome());
        Assertions.assertEquals(2, result.attempt());
    }

    @Test
    public void shouldReturnErrorWhenSavingExecutionFails() {
        TaskExecutionEntity execution = new TaskExecutionEntity();
        execution.setTaskId(202L);

        doThrow(new RuntimeException("db unavailable"))
                .when(executionRepository).save(execution);

        TaskPersistenceApplicationService.ExecutionSaveResult result = service.saveExecution(execution);

        Assertions.assertFalse(result.saved());
        Assertions.assertEquals("db unavailable", result.errorMessage());
    }

    @Test
    public void shouldPersistQualityEvaluationEventWhenExecutionSaved() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(301L);
        task.setPlanId(401L);
        task.setTaskType(TaskTypeEnum.WORKER);
        Map<String, Object> config = new HashMap<>();
        config.put("qualityExperimentKey", "exp-quality");
        config.put("qualityExperimentRolloutPercent", 100);
        task.setConfigSnapshot(config);
        when(taskRepository.findById(301L)).thenReturn(task);

        TaskExecutionEntity execution = new TaskExecutionEntity();
        execution.setId(901L);
        execution.setTaskId(301L);
        execution.setAttemptNumber(1);
        execution.setIsValid(false);
        execution.setValidationFeedback("score=0.71, threshold=0.8");

        when(qualityEventRepository.save(any(QualityEvaluationEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskPersistenceApplicationService.ExecutionSaveResult result = qualityAwareService.saveExecution(execution);

        Assertions.assertTrue(result.saved());
        ArgumentCaptor<QualityEvaluationEventEntity> captor = ArgumentCaptor.forClass(QualityEvaluationEventEntity.class);
        verify(qualityEventRepository, times(1)).save(captor.capture());
        QualityEvaluationEventEntity event = captor.getValue();
        Assertions.assertEquals(401L, event.getPlanId());
        Assertions.assertEquals(301L, event.getTaskId());
        Assertions.assertEquals(901L, event.getExecutionId());
        Assertions.assertEquals("WORKER_VALIDATOR", event.getEvaluatorType());
        Assertions.assertEquals("exp-quality", event.getExperimentKey());
        Assertions.assertEquals("B", event.getExperimentVariant());
        Assertions.assertEquals("v1", event.getSchemaVersion());
        Assertions.assertEquals(0.71D, event.getScore());
        Assertions.assertFalse(event.getPass());
        Assertions.assertEquals("score=0.71, threshold=0.8", event.getFeedback());
        Assertions.assertEquals(100.0D, event.getPayload().get("rolloutPercent"));
        Assertions.assertNotNull(event.getPayload().get("bucket"));
    }

    @Test
    public void shouldUseControlVariantWhenQualityExperimentDisabled() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(302L);
        task.setPlanId(402L);
        task.setTaskType(TaskTypeEnum.CRITIC);
        Map<String, Object> config = new HashMap<>();
        config.put("qualityExperimentEnabled", false);
        config.put("qualityExperimentKey", "exp-disabled");
        task.setConfigSnapshot(config);
        when(taskRepository.findById(302L)).thenReturn(task);

        TaskExecutionEntity execution = new TaskExecutionEntity();
        execution.setId(902L);
        execution.setTaskId(302L);
        execution.setAttemptNumber(1);
        execution.setIsValid(true);
        execution.setValidationFeedback("score=0.95");

        when(qualityEventRepository.save(any(QualityEvaluationEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskPersistenceApplicationService.ExecutionSaveResult result = qualityAwareService.saveExecution(execution);

        Assertions.assertTrue(result.saved());
        ArgumentCaptor<QualityEvaluationEventEntity> captor = ArgumentCaptor.forClass(QualityEvaluationEventEntity.class);
        verify(qualityEventRepository, times(1)).save(captor.capture());
        QualityEvaluationEventEntity event = captor.getValue();
        Assertions.assertEquals("CRITIC", event.getEvaluatorType());
        Assertions.assertEquals("exp-disabled", event.getExperimentKey());
        Assertions.assertEquals("CONTROL", event.getExperimentVariant());
        Assertions.assertEquals(Boolean.FALSE, event.getPayload().get("experimentEnabled"));
    }

    @Test
    public void shouldUseRepositoryReturnedExecutionIdWhenPersistingQualityEvent() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(303L);
        task.setPlanId(403L);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setConfigSnapshot(new HashMap<>());
        when(taskRepository.findById(303L)).thenReturn(task);

        TaskExecutionEntity requestExecution = new TaskExecutionEntity();
        requestExecution.setTaskId(303L);
        requestExecution.setAttemptNumber(2);
        requestExecution.setIsValid(true);
        requestExecution.setValidationFeedback("score=0.90");

        TaskExecutionEntity savedExecution = new TaskExecutionEntity();
        savedExecution.setId(999L);
        savedExecution.setTaskId(303L);
        savedExecution.setAttemptNumber(2);
        savedExecution.setIsValid(true);
        savedExecution.setValidationFeedback("score=0.90");
        when(executionRepository.save(requestExecution)).thenReturn(savedExecution);
        when(qualityEventRepository.save(any(QualityEvaluationEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskPersistenceApplicationService.ExecutionSaveResult result = qualityAwareService.saveExecution(requestExecution);

        Assertions.assertTrue(result.saved());
        ArgumentCaptor<QualityEvaluationEventEntity> captor = ArgumentCaptor.forClass(QualityEvaluationEventEntity.class);
        verify(qualityEventRepository, times(1)).save(captor.capture());
        Assertions.assertEquals(999L, captor.getValue().getExecutionId());
    }

    private AgentPlanEntity buildPlan(Long id, Integer version, Map<String, Object> context) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(id);
        plan.setVersion(version);
        plan.setGlobalContext(new HashMap<>(context));
        return plan;
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }
}
