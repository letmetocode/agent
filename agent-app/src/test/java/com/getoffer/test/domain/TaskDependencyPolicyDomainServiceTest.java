package com.getoffer.test.domain;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskDependencyPolicy;
import com.getoffer.domain.task.service.TaskDependencyPolicyDomainService;
import com.getoffer.types.enums.TaskStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskDependencyPolicyDomainServiceTest {

    private final TaskDependencyPolicyDomainService service = new TaskDependencyPolicyDomainService();

    @Test
    public void shouldReturnSatisfiedWhenAllDependenciesCompleted() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setStatus(TaskStatusEnum.PENDING);
        task.setDependencyNodeIds(List.of("n1", "n2"));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.COMPLETED);
        statusByNode.put("n2", TaskStatusEnum.COMPLETED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.SATISFIED, decision);
    }

    @Test
    public void shouldReturnBlockedWhenAnyDependencyFailedOrSkipped() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setStatus(TaskStatusEnum.PENDING);
        task.setDependencyNodeIds(List.of("n1"));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.FAILED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.BLOCKED, decision);
    }

    @Test
    public void shouldReturnWaitingWhenDependencyMissingOrNotCompleted() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setStatus(TaskStatusEnum.PENDING);
        task.setDependencyNodeIds(List.of("n1"));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.RUNNING);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.WAITING, decision);
    }
}
