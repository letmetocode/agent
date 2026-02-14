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
        AgentTaskEntity task = newTask(List.of("n1", "n2"), null);

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.COMPLETED);
        statusByNode.put("n2", TaskStatusEnum.COMPLETED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.SATISFIED, decision);
    }

    @Test
    public void shouldReturnBlockedWhenAnyDependencyFailedOrSkippedByDefault() {
        AgentTaskEntity task = newTask(List.of("n1"), null);

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.FAILED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.BLOCKED, decision);
    }

    @Test
    public void shouldReturnWaitingWhenDependencyMissingOrNotCompleted() {
        AgentTaskEntity task = newTask(List.of("n1"), null);

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.RUNNING);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.WAITING, decision);
    }

    @Test
    public void shouldSupportAnyJoinPolicy() {
        AgentTaskEntity task = newTask(List.of("n1", "n2"), Map.of(
                "joinPolicy", "any",
                "failurePolicy", "failSafe"
        ));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.FAILED);
        statusByNode.put("n2", TaskStatusEnum.COMPLETED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.SATISFIED, decision);
    }

    @Test
    public void shouldSupportQuorumJoinPolicy() {
        AgentTaskEntity task = newTask(List.of("n1", "n2", "n3"), Map.of(
                "joinPolicy", "quorum",
                "quorum", 2,
                "failurePolicy", "failSafe"
        ));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.COMPLETED);
        statusByNode.put("n2", TaskStatusEnum.FAILED);
        statusByNode.put("n3", TaskStatusEnum.COMPLETED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.SATISFIED, decision);
    }

    @Test
    public void shouldReturnSatisfiedWhenFailSafeAllDependenciesTerminal() {
        AgentTaskEntity task = newTask(List.of("n1", "n2"), Map.of(
                "joinPolicy", "all",
                "failurePolicy", "failSafe"
        ));

        Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
        statusByNode.put("n1", TaskStatusEnum.FAILED);
        statusByNode.put("n2", TaskStatusEnum.SKIPPED);

        TaskDependencyPolicy.DependencyDecision decision =
                service.resolveDependencyDecision(task, statusByNode);

        Assertions.assertEquals(TaskDependencyPolicy.DependencyDecision.SATISFIED, decision);
    }

    private AgentTaskEntity newTask(List<String> dependencies,
                                    Map<String, Object> graphPolicy) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setStatus(TaskStatusEnum.PENDING);
        task.setDependencyNodeIds(dependencies);

        Map<String, Object> config = new HashMap<>();
        if (graphPolicy != null && !graphPolicy.isEmpty()) {
            config.put("graphPolicy", new HashMap<>(graphPolicy));
        }
        task.setConfigSnapshot(config);
        return task;
    }
}
