package com.getoffer.test;

import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.domain.task.service.TaskDependencyPolicyDomainService;
import com.getoffer.trigger.application.command.TaskScheduleApplicationService;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TaskScheduleApplicationServiceTest {

    @Test
    public void shouldPromoteTaskWhenDependenciesSatisfied() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        repository.save(newTask(1L, 100L, "dep", TaskStatusEnum.COMPLETED, Collections.emptyList()));
        repository.save(newTask(2L, 100L, "work", TaskStatusEnum.PENDING, List.of("dep")));

        TaskScheduleApplicationService service = new TaskScheduleApplicationService(
                repository,
                new TaskDependencyPolicyDomainService()
        );

        TaskScheduleApplicationService.ScheduleResult result = service.schedulePendingTasks();

        Assertions.assertEquals(1, result.pendingCount());
        Assertions.assertEquals(1, result.promotedCount());
        Assertions.assertEquals(TaskStatusEnum.READY, repository.findById(2L).getStatus());
    }

    @Test
    public void shouldSkipTaskWhenDependencyBlocked() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        repository.save(newTask(3L, 200L, "dep", TaskStatusEnum.FAILED, Collections.emptyList()));
        repository.save(newTask(4L, 200L, "work", TaskStatusEnum.PENDING, List.of("dep")));

        TaskScheduleApplicationService service = new TaskScheduleApplicationService(
                repository,
                new TaskDependencyPolicyDomainService()
        );

        TaskScheduleApplicationService.ScheduleResult result = service.schedulePendingTasks();

        Assertions.assertEquals(1, result.pendingCount());
        Assertions.assertEquals(1, result.skippedCount());
        Assertions.assertEquals(TaskStatusEnum.SKIPPED, repository.findById(4L).getStatus());
    }

    @Test
    public void shouldKeepTaskWaitingWhenDependencyNotCompleted() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        repository.save(newTask(5L, 300L, "dep", TaskStatusEnum.RUNNING, Collections.emptyList()));
        repository.save(newTask(6L, 300L, "work", TaskStatusEnum.PENDING, List.of("dep")));

        TaskScheduleApplicationService service = new TaskScheduleApplicationService(
                repository,
                new TaskDependencyPolicyDomainService()
        );

        TaskScheduleApplicationService.ScheduleResult result = service.schedulePendingTasks();

        Assertions.assertEquals(1, result.pendingCount());
        Assertions.assertEquals(1, result.waitingCount());
        Assertions.assertEquals(TaskStatusEnum.PENDING, repository.findById(6L).getStatus());
    }

    @Test
    public void shouldCountErrorWhenUpdateFails() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        repository.save(newTask(7L, 400L, "dep", TaskStatusEnum.COMPLETED, Collections.emptyList()));
        repository.save(newTask(8L, 400L, "work", TaskStatusEnum.PENDING, List.of("dep")));
        repository.setFailUpdateTaskId(8L);

        TaskScheduleApplicationService service = new TaskScheduleApplicationService(
                repository,
                new TaskDependencyPolicyDomainService()
        );

        TaskScheduleApplicationService.ScheduleResult result = service.schedulePendingTasks();

        Assertions.assertEquals(1, result.errorCount());
        Assertions.assertEquals(0, result.promotedCount());
    }

    private AgentTaskEntity newTask(Long id,
                                    Long planId,
                                    String nodeId,
                                    TaskStatusEnum status,
                                    List<String> dependencies) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(id);
        task.setPlanId(planId);
        task.setNodeId(nodeId);
        task.setName("task-" + id);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setStatus(status);
        task.setDependencyNodeIds(dependencies);
        task.setConfigSnapshot(new HashMap<>());
        task.setInputContext(new HashMap<>());
        task.setVersion(0);
        return task;
    }

    private static final class InMemoryAgentTaskRepository implements IAgentTaskRepository {
        private final Map<Long, AgentTaskEntity> store = new LinkedHashMap<>();
        private Long failUpdateTaskId;

        public void setFailUpdateTaskId(Long failUpdateTaskId) {
            this.failUpdateTaskId = failUpdateTaskId;
        }

        @Override
        public AgentTaskEntity save(AgentTaskEntity entity) {
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public AgentTaskEntity update(AgentTaskEntity entity) {
            if (Objects.equals(failUpdateTaskId, entity.getId())) {
                throw new RuntimeException("mock update failure");
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return store.remove(id) != null;
        }

        @Override
        public AgentTaskEntity findById(Long id) {
            return store.get(id);
        }

        @Override
        public List<AgentTaskEntity> findByPlanId(Long planId) {
            return store.values().stream()
                    .filter(task -> Objects.equals(planId, task.getPlanId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentTaskEntity> findByPlanIdAndStatus(Long planId, TaskStatusEnum status) {
            return store.values().stream()
                    .filter(task -> Objects.equals(planId, task.getPlanId()))
                    .filter(task -> Objects.equals(status, task.getStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public AgentTaskEntity findByPlanIdAndNodeId(Long planId, String nodeId) {
            return store.values().stream()
                    .filter(task -> Objects.equals(planId, task.getPlanId()))
                    .filter(task -> Objects.equals(nodeId, task.getNodeId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<AgentTaskEntity> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<AgentTaskEntity> findByStatus(TaskStatusEnum status) {
            return store.values().stream()
                    .filter(task -> Objects.equals(status, task.getStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentTaskEntity> findReadyTasks() {
            return findByStatus(TaskStatusEnum.READY);
        }

        @Override
        public List<AgentTaskEntity> claimExecutableTasks(String claimOwner, int limit, int leaseSeconds) {
            return Collections.emptyList();
        }

        @Override
        public boolean renewClaimLease(Long taskId, String claimOwner, Integer executionAttempt, int leaseSeconds) {
            return false;
        }

        @Override
        public boolean updateClaimedTaskState(AgentTaskEntity entity) {
            return false;
        }

        @Override
        public long countExpiredRunningTasks() {
            return 0;
        }

        @Override
        public List<AgentTaskEntity> batchSave(List<AgentTaskEntity> entities) {
            if (entities == null || entities.isEmpty()) {
                return Collections.emptyList();
            }
            for (AgentTaskEntity entity : entities) {
                save(entity);
            }
            return entities;
        }

        @Override
        public boolean batchUpdateStatus(Long planId, TaskStatusEnum fromStatus, TaskStatusEnum toStatus) {
            return false;
        }

        @Override
        public List<PlanTaskStatusStat> summarizeByPlanIds(List<Long> planIds) {
            return Collections.emptyList();
        }
    }
}
