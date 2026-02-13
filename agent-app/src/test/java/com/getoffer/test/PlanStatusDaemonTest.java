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
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

public class PlanStatusDaemonTest {

    @Test
    public void shouldPromoteReadyPlanToRunningWhenAnyTaskRunningLike() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

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
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

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
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

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
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

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
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

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
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository);

        AgentPlanEntity plan = newPlan(6L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(601L, 6L, TaskStatusEnum.SKIPPED));
        taskRepository.save(newTask(602L, 6L, TaskStatusEnum.SKIPPED));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, planRepository.findById(6L).getStatus());
    }

    @Test
    public void shouldPublishPlanFinishedOnceWhenTurnAlreadyFinalized() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository, turnRepository, messageRepository);

        AgentPlanEntity plan = newPlan(7L, PlanStatusEnum.RUNNING);
        planRepository.save(plan);
        taskRepository.save(newTask(701L, 7L, TaskStatusEnum.COMPLETED));
        turnRepository.addTurn(7L, newTurn(70L, 1007L, 7L, TurnStatusEnum.COMPLETED, 7001L, "already-final"));

        daemon.syncPlanStatuses();

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, planRepository.findById(7L).getStatus());
        Assertions.assertEquals(1, eventRepository.countByType(PlanTaskEventTypeEnum.PLAN_FINISHED));
        Assertions.assertEquals(0, messageRepository.totalMessages());
    }

    @Test
    public void shouldFinalizeOnlyOnceWhenPlanReconciledRepeatedly() {
        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        InMemoryPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        PlanStatusDaemon daemon = newDaemon(planRepository, taskRepository, eventRepository, turnRepository, messageRepository);

        AgentPlanEntity plan = newPlan(8L, PlanStatusEnum.READY);
        planRepository.save(plan);
        taskRepository.save(newTask(801L, 8L, TaskStatusEnum.COMPLETED));
        turnRepository.addTurn(8L, newTurn(80L, 1008L, 8L, TurnStatusEnum.EXECUTING, null, null));

        daemon.syncPlanStatuses();
        daemon.syncPlanStatuses();

        SessionTurnEntity turn = turnRepository.findByPlanId(8L);
        Assertions.assertNotNull(turn);
        Assertions.assertEquals(TurnStatusEnum.COMPLETED, turn.getStatus());
        Assertions.assertNotNull(turn.getFinalResponseMessageId());
        Assertions.assertEquals(1, messageRepository.totalMessages());
        Assertions.assertEquals(1, eventRepository.countByType(PlanTaskEventTypeEnum.PLAN_FINISHED));
    }

    private AgentPlanEntity newPlan(Long id, PlanStatusEnum status) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(id);
        plan.setSessionId(1000L + id);
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

    private PlanStatusDaemon newDaemon(InMemoryAgentPlanRepository planRepository,
                                       InMemoryAgentTaskRepository taskRepository,
                                       InMemoryPlanTaskEventRepository eventRepository) {
        return newDaemon(planRepository,
                taskRepository,
                eventRepository,
                new InMemorySessionTurnRepository(),
                new InMemorySessionMessageRepository());
    }

    private PlanStatusDaemon newDaemon(InMemoryAgentPlanRepository planRepository,
                                       InMemoryAgentTaskRepository taskRepository,
                                       InMemoryPlanTaskEventRepository eventRepository,
                                       InMemorySessionTurnRepository turnRepository,
                                       InMemorySessionMessageRepository messageRepository) {
        TurnFinalizeApplicationService turnResultService = new TurnFinalizeApplicationService(
                turnRepository,
                messageRepository,
                taskRepository
        );
        return new PlanStatusDaemon(planRepository, taskRepository, new PlanTaskEventPublisher(eventRepository), turnResultService, 100, 1000);
    }

    private SessionTurnEntity newTurn(Long turnId,
                                      Long sessionId,
                                      Long planId,
                                      TurnStatusEnum status,
                                      Long finalMessageId,
                                      String summary) {
        SessionTurnEntity turn = new SessionTurnEntity();
        turn.setId(turnId);
        turn.setSessionId(sessionId);
        turn.setPlanId(planId);
        turn.setUserMessage("user-message");
        turn.setStatus(status);
        turn.setFinalResponseMessageId(finalMessageId);
        turn.setAssistantSummary(summary);
        turn.setCompletedAt(LocalDateTime.now());
        return turn;
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
        public List<AgentPlanEntity> findByWorkflowDefinitionId(Long workflowDefinitionId) {
            return store.values().stream()
                    .filter(plan -> Objects.equals(workflowDefinitionId, plan.getWorkflowDefinitionId()))
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

    private static final class InMemorySessionTurnRepository implements com.getoffer.domain.session.adapter.repository.ISessionTurnRepository {
        private final Map<Long, com.getoffer.domain.session.model.entity.SessionTurnEntity> byPlanId = new HashMap<>();
        private final Map<Long, com.getoffer.domain.session.model.entity.SessionTurnEntity> byTurnId = new HashMap<>();

        public void addTurn(Long planId, com.getoffer.domain.session.model.entity.SessionTurnEntity turn) {
            byPlanId.put(planId, turn);
            byTurnId.put(turn.getId(), turn);
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionTurnEntity save(com.getoffer.domain.session.model.entity.SessionTurnEntity entity) {
            if (entity != null && entity.getPlanId() != null) {
                addTurn(entity.getPlanId(), entity);
            }
            return entity;
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionTurnEntity update(com.getoffer.domain.session.model.entity.SessionTurnEntity entity) {
            if (entity != null && entity.getPlanId() != null) {
                addTurn(entity.getPlanId(), entity);
            }
            return entity;
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionTurnEntity findById(Long id) {
            return byTurnId.get(id);
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionTurnEntity findByPlanId(Long planId) {
            return byPlanId.get(planId);
        }

        @Override
        public boolean markTerminalIfNotTerminal(Long turnId,
                                                 com.getoffer.types.enums.TurnStatusEnum status,
                                                 String assistantSummary,
                                                 LocalDateTime completedAt) {
            com.getoffer.domain.session.model.entity.SessionTurnEntity turn = byTurnId.get(turnId);
            if (turn == null || turn.isTerminal()) {
                return false;
            }
            turn.setStatus(status);
            turn.setAssistantSummary(assistantSummary);
            turn.setCompletedAt(completedAt);
            return true;
        }

        @Override
        public boolean bindFinalResponseMessage(Long turnId, Long messageId) {
            com.getoffer.domain.session.model.entity.SessionTurnEntity turn = byTurnId.get(turnId);
            if (turn == null || turn.getFinalResponseMessageId() != null) {
                return false;
            }
            turn.setFinalResponseMessageId(messageId);
            return true;
        }

        @Override
        public List<com.getoffer.domain.session.model.entity.SessionTurnEntity> findBySessionId(Long sessionId) {
            return Collections.emptyList();
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionTurnEntity findLatestBySessionIdAndStatus(Long sessionId, com.getoffer.types.enums.TurnStatusEnum status) {
            return null;
        }
    }

    private static final class InMemorySessionMessageRepository implements com.getoffer.domain.session.adapter.repository.ISessionMessageRepository {
        private long seq = 1L;
        private final Map<Long, List<com.getoffer.domain.session.model.entity.SessionMessageEntity>> byTurnId = new HashMap<>();

        @Override
        public com.getoffer.domain.session.model.entity.SessionMessageEntity save(com.getoffer.domain.session.model.entity.SessionMessageEntity entity) {
            entity.setId(seq++);
            byTurnId.computeIfAbsent(entity.getTurnId(), key -> new ArrayList<>()).add(entity);
            return entity;
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionMessageEntity saveAssistantFinalMessageIfAbsent(com.getoffer.domain.session.model.entity.SessionMessageEntity entity) {
            List<com.getoffer.domain.session.model.entity.SessionMessageEntity> messages = byTurnId.get(entity.getTurnId());
            if (messages != null && !messages.isEmpty()) {
                return messages.get(messages.size() - 1);
            }
            return save(entity);
        }

        @Override
        public com.getoffer.domain.session.model.entity.SessionMessageEntity findById(Long id) {
            if (id == null) {
                return null;
            }
            for (List<com.getoffer.domain.session.model.entity.SessionMessageEntity> messages : byTurnId.values()) {
                for (com.getoffer.domain.session.model.entity.SessionMessageEntity message : messages) {
                    if (id.equals(message.getId())) {
                        return message;
                    }
                }
            }
            return null;
        }

        @Override
        public List<com.getoffer.domain.session.model.entity.SessionMessageEntity> findByTurnId(Long turnId) {
            if (turnId == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(byTurnId.getOrDefault(turnId, Collections.emptyList()));
        }

        @Override
        public List<com.getoffer.domain.session.model.entity.SessionMessageEntity> findBySessionId(Long sessionId) {
            if (sessionId == null) {
                return Collections.emptyList();
            }
            return byTurnId.values().stream()
                    .flatMap(List::stream)
                    .filter(message -> Objects.equals(sessionId, message.getSessionId()))
                    .collect(Collectors.toList());
        }

        public int totalMessages() {
            return byTurnId.values().stream().mapToInt(List::size).sum();
        }
    }

    private static final class InMemoryPlanTaskEventRepository implements IPlanTaskEventRepository {
        private final List<PlanTaskEventEntity> store = new ArrayList<>();
        private long seq = 1L;

        @Override
        public PlanTaskEventEntity save(PlanTaskEventEntity entity) {
            entity.setId(seq++);
            store.add(entity);
            return entity;
        }

        @Override
        public List<PlanTaskEventEntity> findByPlanIdAfterEventId(Long planId, Long afterEventId, int limit) {
            long cursor = afterEventId == null ? 0L : afterEventId;
            int safeLimit = limit <= 0 ? 200 : limit;
            return store.stream()
                    .filter(event -> Objects.equals(planId, event.getPlanId()))
                    .filter(event -> event.getId() != null && event.getId() > cursor)
                    .limit(safeLimit)
                    .collect(Collectors.toList());
        }

        public long countByType(PlanTaskEventTypeEnum eventType) {
            return store.stream().filter(event -> eventType == event.getEventType()).count();
        }
    }
}
