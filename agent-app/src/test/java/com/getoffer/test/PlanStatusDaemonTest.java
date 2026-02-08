package com.getoffer.test;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.job.PlanStatusDaemon;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanStatusEnum;
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

public class PlanStatusDaemonTest {

    @Test
    public void shouldPromoteReadyPlanToRunningWhenAnyTaskRunningLike() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(1L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(101L, 1L, TaskStatusEnum.RUNNING));
        taskRepository.save(newTask(102L, 1L, TaskStatusEnum.PENDING));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.RUNNING, planRepository.findById(1L).getStatus());
    }

    @Test
    public void shouldCompleteRunningPlanWhenAllTasksTerminalWithoutFailed() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(2L, PlanStatusEnum.RUNNING);
        planRepository.save(plan);
        taskRepository.save(newTask(201L, 2L, TaskStatusEnum.COMPLETED));
        taskRepository.save(newTask(202L, 2L, TaskStatusEnum.SKIPPED));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, planRepository.findById(2L).getStatus());
    }

    @Test
    public void shouldCompleteReadyPlanDirectlyWhenAllTasksTerminalWithoutFailed() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(3L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(301L, 3L, TaskStatusEnum.COMPLETED));
        taskRepository.save(newTask(302L, 3L, TaskStatusEnum.SKIPPED));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, planRepository.findById(3L).getStatus());
    }

    @Test
    public void shouldFailPlanWhenAnyTaskFailed() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(4L, PlanStatusEnum.RUNNING);
        planRepository.save(plan);
        taskRepository.save(newTask(401L, 4L, TaskStatusEnum.FAILED));
        taskRepository.save(newTask(402L, 4L, TaskStatusEnum.COMPLETED));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.FAILED, planRepository.findById(4L).getStatus());
    }

    @Test
    public void shouldKeepReadyWhenNoRunningAndNotAllTerminal() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(5L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(501L, 5L, TaskStatusEnum.PENDING));
        taskRepository.save(newTask(502L, 5L, TaskStatusEnum.READY));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.READY, planRepository.findById(5L).getStatus());
    }

    @Test
    public void shouldCompleteWhenAllTasksSkippedWithoutFailed() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), 100, 1000);

        AgentPlanEntity plan = newPlan(6L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(601L, 6L, TaskStatusEnum.SKIPPED));
        taskRepository.save(newTask(602L, 6L, TaskStatusEnum.SKIPPED));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, planRepository.findById(6L).getStatus());
    }

    private AgentPlanEntity newPlan(Long id, PlanStatusEnum status) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(id);
        plan.setSessionId(1000L + id);
        plan.setPlanGoal("plan-" + id);
        plan.setExecutionGraph(Collections.singletonMap("nodes", Collections.emptyList()));
        plan.setGlobalContext(new HashMap<>());
        plan.setStatus(status);
        plan.setPriority(0);
        plan.setVersion(0);
        return plan;
    }

    private AgentTaskEntity newTask(Long id, Long planId, TaskStatusEnum status) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(id);
        task.setPlanId(planId);
        task.setNodeId("node-" + id);
        task.setName("task-" + id);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setStatus(status);
        task.setDependencyNodeIds(Collections.emptyList());
        task.setConfigSnapshot(new HashMap<>());
        task.setInputContext(new HashMap<>());
        task.setVersion(0);
        return task;
    }

    private static final class InMemoryAgentPlanRepository implements IAgentPlanRepository {

        private final Map<Long, AgentPlanEntity> store = new LinkedHashMap<>();

        @Override
        public AgentPlanEntity save(AgentPlanEntity entity) {
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public AgentPlanEntity update(AgentPlanEntity entity) {
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return store.remove(id) != null;
        }

        @Override
        public AgentPlanEntity findById(Long id) {
            return store.get(id);
        }

        @Override
        public List<AgentPlanEntity> findBySessionId(Long sessionId) {
            return store.values().stream()
                    .filter(plan -> Objects.equals(sessionId, plan.getSessionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentPlanEntity> findByStatus(PlanStatusEnum status) {
            return store.values().stream()
                    .filter(plan -> Objects.equals(status, plan.getStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status) {
            return findByStatus(status);
        }

        @Override
        public List<AgentPlanEntity> findByStatusPaged(PlanStatusEnum status, int offset, int limit) {
            if (limit <= 0) {
                return Collections.emptyList();
            }
            List<AgentPlanEntity> matched = findByStatus(status);
            if (matched.isEmpty() || offset >= matched.size()) {
                return Collections.emptyList();
            }
            int from = Math.max(0, offset);
            int to = Math.min(from + limit, matched.size());
            return new ArrayList<>(matched.subList(from, to));
        }

        @Override
        public List<AgentPlanEntity> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<AgentPlanEntity> findBySopTemplateId(Long sopTemplateId) {
            return store.values().stream()
                    .filter(plan -> Objects.equals(sopTemplateId, plan.getSopTemplateId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentPlanEntity> findExecutablePlans() {
            return store.values().stream()
                    .filter(plan -> plan.getStatus() == PlanStatusEnum.READY || plan.getStatus() == PlanStatusEnum.RUNNING)
                    .collect(Collectors.toList());
        }
    }

    private static final class InMemoryAgentTaskRepository implements IAgentTaskRepository {

        private final Map<Long, AgentTaskEntity> store = new LinkedHashMap<>();

        @Override
        public AgentTaskEntity save(AgentTaskEntity entity) {
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public AgentTaskEntity update(AgentTaskEntity entity) {
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
            return 0L;
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
            boolean updated = false;
            for (AgentTaskEntity task : store.values()) {
                if (!Objects.equals(planId, task.getPlanId())) {
                    continue;
                }
                if (!Objects.equals(fromStatus, task.getStatus())) {
                    continue;
                }
                task.setStatus(toStatus);
                updated = true;
            }
            return updated;
        }

        @Override
        public List<PlanTaskStatusStat> summarizeByPlanIds(List<Long> planIds) {
            if (planIds == null || planIds.isEmpty()) {
                return Collections.emptyList();
            }
            List<PlanTaskStatusStat> result = new ArrayList<>();
            for (Long planId : planIds) {
                List<AgentTaskEntity> tasks = findByPlanId(planId);
                if (tasks.isEmpty()) {
                    continue;
                }
                long total = 0;
                long failedCount = 0;
                long runningLikeCount = 0;
                long terminalCount = 0;

                for (AgentTaskEntity task : tasks) {
                    if (task == null || task.getStatus() == null) {
                        continue;
                    }
                    total++;
                    TaskStatusEnum status = task.getStatus();
                    if (status == TaskStatusEnum.FAILED) {
                        failedCount++;
                    }
                    if (status == TaskStatusEnum.RUNNING
                            || status == TaskStatusEnum.VALIDATING
                            || status == TaskStatusEnum.REFINING) {
                        runningLikeCount++;
                    }
                    if (status == TaskStatusEnum.COMPLETED
                            || status == TaskStatusEnum.FAILED
                            || status == TaskStatusEnum.SKIPPED) {
                        terminalCount++;
                    }
                }
                if (total <= 0) {
                    continue;
                }
                result.add(PlanTaskStatusStat.builder()
                        .planId(planId)
                        .total(total)
                        .failedCount(failedCount)
                        .runningLikeCount(runningLikeCount)
                        .terminalCount(terminalCount)
                        .build());
            }
            return result;
        }
    }

    private static final class InMemoryPlanTaskEventRepository implements IPlanTaskEventRepository {

        @Override
        public PlanTaskEventEntity save(PlanTaskEventEntity entity) {
            return entity;
        }

        @Override
        public List<PlanTaskEventEntity> findByPlanIdAfterEventId(Long planId, Long afterEventId, int limit) {
            return Collections.emptyList();
        }
    }
}
