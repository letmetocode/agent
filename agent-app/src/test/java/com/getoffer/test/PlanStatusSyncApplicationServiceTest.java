package com.getoffer.test;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.application.command.PlanStatusSyncApplicationService;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlanStatusSyncApplicationServiceTest {

    @Test
    public void shouldFinalizeAndPublishWhenPlanBecomesTerminal() {
        IAgentPlanRepository planRepository = mock(IAgentPlanRepository.class);
        IAgentTaskRepository taskRepository = mock(IAgentTaskRepository.class);
        PlanTaskEventPublisher eventPublisher = mock(PlanTaskEventPublisher.class);
        TurnFinalizeApplicationService turnFinalizeService = mock(TurnFinalizeApplicationService.class);

        AgentPlanEntity plan = newPlan(1L, PlanStatusEnum.RUNNING);
        PlanTaskStatusStat stat = PlanTaskStatusStat.builder()
                .planId(1L)
                .total(1L)
                .failedCount(0L)
                .runningLikeCount(0L)
                .terminalCount(1L)
                .build();

        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.READY), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());
        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.RUNNING), eq(0), eq(100)))
                .thenReturn(List.of(plan));
        when(taskRepository.summarizeByPlanIds(any())).thenReturn(List.of(stat));
        when(turnFinalizeService.finalizeByPlan(1L, PlanStatusEnum.COMPLETED))
                .thenReturn(TurnFinalizeApplicationService.TurnFinalizeResult.of(
                        11L,
                        22L,
                        "summary",
                        TurnStatusEnum.COMPLETED,
                        TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED
                ));

        PlanStatusSyncApplicationService service = new PlanStatusSyncApplicationService(
                planRepository,
                taskRepository,
                eventPublisher,
                turnFinalizeService,
                new PlanTransitionDomainService()
        );

        PlanStatusSyncApplicationService.SyncResult result = service.syncPlanStatuses(100, 1000);

        Assertions.assertEquals(1, result.processedCount());
        Assertions.assertEquals(1, result.advancedCount());
        Assertions.assertEquals(1, result.finalizeAttemptCount());
        Assertions.assertEquals(1, result.finalizeDedupCount());
        Assertions.assertEquals(1, result.finishedPublishCount());
        Assertions.assertEquals(0, result.errorCount());
        verify(planRepository, times(1)).update(plan);
        verify(eventPublisher, times(1)).publish(eq(PlanTaskEventTypeEnum.PLAN_FINISHED), eq(1L), eq(null), any());
    }

    @Test
    public void shouldSkipWhenOptimisticLockHappened() {
        IAgentPlanRepository planRepository = mock(IAgentPlanRepository.class);
        IAgentTaskRepository taskRepository = mock(IAgentTaskRepository.class);
        PlanTaskEventPublisher eventPublisher = mock(PlanTaskEventPublisher.class);
        TurnFinalizeApplicationService turnFinalizeService = mock(TurnFinalizeApplicationService.class);

        AgentPlanEntity plan = newPlan(2L, PlanStatusEnum.RUNNING);
        PlanTaskStatusStat stat = PlanTaskStatusStat.builder()
                .planId(2L)
                .total(1L)
                .failedCount(0L)
                .runningLikeCount(0L)
                .terminalCount(1L)
                .build();

        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.READY), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());
        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.RUNNING), eq(0), eq(100)))
                .thenReturn(List.of(plan));
        when(taskRepository.summarizeByPlanIds(any())).thenReturn(List.of(stat));
        when(planRepository.update(plan)).thenThrow(new RuntimeException("Optimistic lock conflict"));

        PlanStatusSyncApplicationService service = new PlanStatusSyncApplicationService(
                planRepository,
                taskRepository,
                eventPublisher,
                turnFinalizeService,
                new PlanTransitionDomainService()
        );

        PlanStatusSyncApplicationService.SyncResult result = service.syncPlanStatuses(100, 1000);

        Assertions.assertEquals(1, result.processedCount());
        Assertions.assertEquals(0, result.advancedCount());
        Assertions.assertEquals(0, result.finalizeAttemptCount());
        Assertions.assertEquals(0, result.errorCount());
    }

    @Test
    public void shouldCountErrorWhenUnexpectedUpdateErrorOccurs() {
        IAgentPlanRepository planRepository = mock(IAgentPlanRepository.class);
        IAgentTaskRepository taskRepository = mock(IAgentTaskRepository.class);
        PlanTaskEventPublisher eventPublisher = mock(PlanTaskEventPublisher.class);
        TurnFinalizeApplicationService turnFinalizeService = mock(TurnFinalizeApplicationService.class);

        AgentPlanEntity plan = newPlan(3L, PlanStatusEnum.READY);
        PlanTaskStatusStat stat = PlanTaskStatusStat.builder()
                .planId(3L)
                .total(1L)
                .failedCount(0L)
                .runningLikeCount(1L)
                .terminalCount(0L)
                .build();

        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.READY), eq(0), eq(100)))
                .thenReturn(List.of(plan));
        when(planRepository.findByStatusPaged(eq(PlanStatusEnum.RUNNING), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.summarizeByPlanIds(any())).thenReturn(List.of(stat));
        when(planRepository.update(plan)).thenThrow(new RuntimeException("database unavailable"));

        PlanStatusSyncApplicationService service = new PlanStatusSyncApplicationService(
                planRepository,
                taskRepository,
                eventPublisher,
                turnFinalizeService,
                new PlanTransitionDomainService()
        );

        PlanStatusSyncApplicationService.SyncResult result = service.syncPlanStatuses(100, 1000);

        Assertions.assertEquals(1, result.processedCount());
        Assertions.assertEquals(0, result.advancedCount());
        Assertions.assertEquals(1, result.errorCount());
    }

    private AgentPlanEntity newPlan(Long id, PlanStatusEnum status) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(id);
        plan.setSessionId(900L + id);
        plan.setRouteDecisionId(1L);
        plan.setPlanGoal("plan-" + id);
        plan.setExecutionGraph(Collections.singletonMap("nodes", Collections.emptyList()));
        plan.setDefinitionSnapshot(Collections.singletonMap("routeType", "HIT_PRODUCTION"));
        plan.setGlobalContext(new HashMap<>());
        plan.setStatus(status);
        plan.setPriority(0);
        plan.setVersion(0);
        return plan;
    }
}
