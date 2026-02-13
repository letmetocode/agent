package com.getoffer.test;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskPersistencePolicyDomainService;
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    private final TaskPersistenceApplicationService service = new TaskPersistenceApplicationService(
            taskRepository,
            executionRepository,
            planRepository,
            new TaskBlackboardDomainService(),
            new TaskPersistencePolicyDomainService()
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
