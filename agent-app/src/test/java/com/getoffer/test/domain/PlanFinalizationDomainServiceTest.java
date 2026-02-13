package com.getoffer.test.domain;

import com.getoffer.domain.planning.service.PlanFinalizationDomainService;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class PlanFinalizationDomainServiceTest {

    private final PlanFinalizationDomainService service = new PlanFinalizationDomainService();

    @Test
    public void shouldSummarizeCompletedWorkerOutputsOnly() {
        AgentTaskEntity worker = task(1L, TaskTypeEnum.WORKER, TaskStatusEnum.COMPLETED, "worker output");
        AgentTaskEntity critic = task(2L, TaskTypeEnum.CRITIC, TaskStatusEnum.COMPLETED, "{\"pass\":true}");

        PlanFinalizationDomainService.FinalizationDecision decision =
                service.resolveFinalization(PlanStatusEnum.COMPLETED, List.of(worker, critic));

        Assertions.assertTrue(decision.finalizable());
        Assertions.assertEquals(TurnStatusEnum.COMPLETED, decision.turnStatus());
        Assertions.assertEquals("worker output", decision.assistantContent());
        Assertions.assertFalse(decision.assistantContent().contains("pass"));
    }

    @Test
    public void shouldUseFailedWorkerDetailWhenPlanFailed() {
        AgentTaskEntity worker = task(3L, TaskTypeEnum.WORKER, TaskStatusEnum.FAILED, "timeout");

        PlanFinalizationDomainService.FinalizationDecision decision =
                service.resolveFinalization(PlanStatusEnum.FAILED, List.of(worker));

        Assertions.assertTrue(decision.finalizable());
        Assertions.assertEquals(TurnStatusEnum.FAILED, decision.turnStatus());
        Assertions.assertTrue(decision.assistantContent().contains("timeout"));
    }

    @Test
    public void shouldReturnNotFinalizableWhenPlanStatusNotTerminal() {
        PlanFinalizationDomainService.FinalizationDecision decision =
                service.resolveFinalization(PlanStatusEnum.RUNNING, List.of());

        Assertions.assertFalse(decision.finalizable());
        Assertions.assertNull(decision.turnStatus());
    }

    private AgentTaskEntity task(Long id, TaskTypeEnum type, TaskStatusEnum status, String output) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(id);
        task.setPlanId(100L);
        task.setNodeId("node-" + id);
        task.setName("task-" + id);
        task.setTaskType(type);
        task.setStatus(status);
        task.setOutputResult(output);
        task.setDependencyNodeIds(List.of());
        task.setInputContext(Map.of());
        task.setConfigSnapshot(Map.of());
        task.setVersion(0);
        return task;
    }
}
