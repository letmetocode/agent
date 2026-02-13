package com.getoffer.test;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.planning.service.PlanFinalizationDomainService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

public class TurnResultServiceTest {

    @Test
    public void shouldExcludeCriticOutputWhenPlanCompleted() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        turnRepository.setTurn(buildTurn(100L, 1L, 10L, TurnStatusEnum.EXECUTING));
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        taskRepository.setTasks(10L, List.of(
                buildTask(1001L, 10L, TaskTypeEnum.WORKER, TaskStatusEnum.COMPLETED, "这是最终文案输出"),
                buildTask(1002L, 10L, TaskTypeEnum.CRITIC, TaskStatusEnum.COMPLETED, "{\"pass\":true,\"feedback\":\"ok\"}")
        ));

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        TurnFinalizeApplicationService.TurnFinalizeResult result = service.finalizeByPlan(10L, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals(TurnStatusEnum.COMPLETED, result.getTurnStatus());
        Assertions.assertNotNull(result.getAssistantMessageId());
        Assertions.assertEquals(TurnFinalizeApplicationService.FinalizeOutcome.FINALIZED, result.getOutcome());
        Assertions.assertEquals("这是最终文案输出", messageRepository.getLastSaved().getContent());
        Assertions.assertFalse(messageRepository.getLastSaved().getContent().contains("\"pass\""));
    }

    @Test
    public void shouldReturnEmptyHintWhenOnlyCriticOutputExists() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        turnRepository.setTurn(buildTurn(101L, 1L, 11L, TurnStatusEnum.EXECUTING));
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        taskRepository.setTasks(11L, List.of(
                buildTask(1101L, 11L, TaskTypeEnum.CRITIC, TaskStatusEnum.COMPLETED, "{\"pass\":true,\"feedback\":\"ok\"}")
        ));

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        service.finalizeByPlan(11L, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals("本轮任务已执行完成，但暂无可展示的文本结果。", messageRepository.getLastSaved().getContent());
    }

    @Test
    public void shouldFinalizeFailedTurnWithWorkerFailureDetail() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        turnRepository.setTurn(buildTurn(102L, 1L, 12L, TurnStatusEnum.EXECUTING));
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        taskRepository.setTasks(12L, List.of(
                buildTask(1201L, 12L, TaskTypeEnum.WORKER, TaskStatusEnum.FAILED, "Task execution timed out after 120000 ms"),
                buildTask(1202L, 12L, TaskTypeEnum.CRITIC, TaskStatusEnum.COMPLETED, "{\"pass\":false,\"feedback\":\"bad\"}")
        ));

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        TurnFinalizeApplicationService.TurnFinalizeResult result = service.finalizeByPlan(12L, PlanStatusEnum.FAILED);

        Assertions.assertEquals(TurnStatusEnum.FAILED, result.getTurnStatus());
        Assertions.assertEquals(TurnFinalizeApplicationService.FinalizeOutcome.FINALIZED, result.getOutcome());
        Assertions.assertTrue(messageRepository.getLastSaved().getContent().contains("Task execution timed out"));
        Assertions.assertFalse(messageRepository.getLastSaved().getContent().contains("\"pass\""));
    }

    @Test
    public void shouldReturnAlreadyFinalizedWhenTurnTerminal() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        SessionTurnEntity turn = buildTurn(103L, 1L, 13L, TurnStatusEnum.COMPLETED);
        turn.setFinalResponseMessageId(88L);
        turn.setAssistantSummary("done");
        turnRepository.setTurn(turn);
        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        TurnFinalizeApplicationService.TurnFinalizeResult result = service.finalizeByPlan(13L, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals(TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED, result.getOutcome());
        Assertions.assertEquals(Long.valueOf(88L), result.getAssistantMessageId());
        Assertions.assertTrue(messageRepository.findByTurnId(103L).isEmpty());
    }

    @Test
    public void shouldBackfillFinalMessageWhenTerminalTurnMissingMessageId() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        SessionTurnEntity turn = buildTurn(105L, 1L, 15L, TurnStatusEnum.COMPLETED);
        turn.setAssistantSummary("summary");
        turnRepository.setTurn(turn);

        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        taskRepository.setTasks(15L, List.of(buildTask(1501L, 15L, TaskTypeEnum.WORKER, TaskStatusEnum.COMPLETED, "done")));

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        TurnFinalizeApplicationService.TurnFinalizeResult result = service.finalizeByPlan(15L, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals(TurnFinalizeApplicationService.FinalizeOutcome.FINALIZED, result.getOutcome());
        Assertions.assertNotNull(result.getAssistantMessageId());
        Assertions.assertEquals(1, messageRepository.findByTurnId(105L).size());
    }

    @Test
    public void shouldNotDuplicateFinalMessageWhenMarkTerminalConflict() {
        InMemorySessionTurnRepository turnRepository = new InMemorySessionTurnRepository();
        SessionTurnEntity turn = buildTurn(104L, 1L, 14L, TurnStatusEnum.EXECUTING);
        turnRepository.setTurn(turn);
        turnRepository.setMarkTerminalReturn(false);
        turn.setStatus(TurnStatusEnum.COMPLETED);
        turn.setFinalResponseMessageId(99L);
        turn.setAssistantSummary("existing");

        InMemorySessionMessageRepository messageRepository = new InMemorySessionMessageRepository();
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        taskRepository.setTasks(14L, List.of(buildTask(1401L, 14L, TaskTypeEnum.WORKER, TaskStatusEnum.COMPLETED, "new")));

        TurnFinalizeApplicationService service = new TurnFinalizeApplicationService(turnRepository, messageRepository, taskRepository, new PlanFinalizationDomainService());
        TurnFinalizeApplicationService.TurnFinalizeResult result = service.finalizeByPlan(14L, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals(TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED, result.getOutcome());
        Assertions.assertEquals(Long.valueOf(99L), result.getAssistantMessageId());
        Assertions.assertTrue(messageRepository.findByTurnId(104L).isEmpty());
    }

    private SessionTurnEntity buildTurn(Long turnId, Long sessionId, Long planId, TurnStatusEnum status) {
        SessionTurnEntity turn = new SessionTurnEntity();
        turn.setId(turnId);
        turn.setSessionId(sessionId);
        turn.setPlanId(planId);
        turn.setUserMessage("user-message");
        turn.setStatus(status);
        return turn;
    }

    private AgentTaskEntity buildTask(Long taskId,
                                      Long planId,
                                      TaskTypeEnum taskType,
                                      TaskStatusEnum status,
                                      String output) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(taskId);
        task.setPlanId(planId);
        task.setNodeId("node-" + taskId);
        task.setName("task-" + taskId);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setOutputResult(output);
        task.setDependencyNodeIds(Collections.emptyList());
        task.setInputContext(Collections.emptyMap());
        task.setConfigSnapshot(Collections.emptyMap());
        task.setVersion(0);
        return task;
    }

    private static final class InMemorySessionTurnRepository implements ISessionTurnRepository {
        private SessionTurnEntity turn;
        private boolean markTerminalReturn = true;

        public void setTurn(SessionTurnEntity turn) {
            this.turn = turn;
        }

        @Override
        public SessionTurnEntity save(SessionTurnEntity entity) {
            this.turn = entity;
            return entity;
        }

        @Override
        public SessionTurnEntity update(SessionTurnEntity entity) {
            this.turn = entity;
            return entity;
        }

        @Override
        public SessionTurnEntity findById(Long id) {
            return turn;
        }

        @Override
        public SessionTurnEntity findByPlanId(Long planId) {
            if (turn == null || turn.getPlanId() == null || !turn.getPlanId().equals(planId)) {
                return null;
            }
            return turn;
        }

        public void setMarkTerminalReturn(boolean markTerminalReturn) {
            this.markTerminalReturn = markTerminalReturn;
        }

        @Override
        public boolean markTerminalIfNotTerminal(Long turnId,
                                                 TurnStatusEnum status,
                                                 String assistantSummary,
                                                 LocalDateTime completedAt) {
            if (turn == null || !markTerminalReturn || turn.isTerminal()) {
                return false;
            }
            if (!turn.getId().equals(turnId)) {
                return false;
            }
            turn.setStatus(status);
            turn.setAssistantSummary(assistantSummary);
            turn.setCompletedAt(completedAt);
            return true;
        }

        @Override
        public boolean bindFinalResponseMessage(Long turnId, Long messageId) {
            if (turn == null || turn.getId() == null || !turn.getId().equals(turnId)) {
                return false;
            }
            if (turn.getFinalResponseMessageId() != null) {
                return false;
            }
            turn.setFinalResponseMessageId(messageId);
            return true;
        }

        @Override
        public List<SessionTurnEntity> findBySessionId(Long sessionId) {
            if (turn == null || turn.getSessionId() == null || !turn.getSessionId().equals(sessionId)) {
                return Collections.emptyList();
            }
            return List.of(turn);
        }

        @Override
        public SessionTurnEntity findLatestBySessionIdAndStatus(Long sessionId, TurnStatusEnum status) {
            if (turn == null || turn.getSessionId() == null || turn.getStatus() == null) {
                return null;
            }
            if (!turn.getSessionId().equals(sessionId) || turn.getStatus() != status) {
                return null;
            }
            return turn;
        }
    }

    private static final class InMemorySessionMessageRepository implements ISessionMessageRepository {
        private long sequence = 1L;
        private final List<SessionMessageEntity> saved = new ArrayList<>();

        @Override
        public SessionMessageEntity save(SessionMessageEntity entity) {
            entity.setId(sequence++);
            if (entity.getRole() == null) {
                entity.setRole(MessageRoleEnum.ASSISTANT);
            }
            saved.add(entity);
            return entity;
        }

        @Override
        public SessionMessageEntity saveAssistantFinalMessageIfAbsent(SessionMessageEntity entity) {
            List<SessionMessageEntity> turnMessages = findByTurnId(entity.getTurnId());
            if (!turnMessages.isEmpty()) {
                return turnMessages.get(turnMessages.size() - 1);
            }
            return save(entity);
        }

        @Override
        public SessionMessageEntity findById(Long id) {
            if (id == null) {
                return null;
            }
            for (SessionMessageEntity item : saved) {
                if (id.equals(item.getId())) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public List<SessionMessageEntity> findByTurnId(Long turnId) {
            if (turnId == null) {
                return Collections.emptyList();
            }
            List<SessionMessageEntity> result = new ArrayList<>();
            for (SessionMessageEntity item : saved) {
                if (turnId.equals(item.getTurnId())) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public List<SessionMessageEntity> findBySessionId(Long sessionId) {
            if (sessionId == null) {
                return Collections.emptyList();
            }
            List<SessionMessageEntity> result = new ArrayList<>();
            for (SessionMessageEntity item : saved) {
                if (sessionId.equals(item.getSessionId())) {
                    result.add(item);
                }
            }
            return result;
        }

        public SessionMessageEntity getLastSaved() {
            if (saved.isEmpty()) {
                return null;
            }
            return saved.get(saved.size() - 1);
        }
    }

    private static final class InMemoryAgentTaskRepository implements IAgentTaskRepository {
        private final Map<Long, List<AgentTaskEntity>> tasksByPlanId = new HashMap<>();

        public void setTasks(Long planId, List<AgentTaskEntity> tasks) {
            tasksByPlanId.put(planId, tasks == null ? Collections.emptyList() : tasks);
        }

        @Override
        public AgentTaskEntity save(AgentTaskEntity entity) {
            return entity;
        }

        @Override
        public AgentTaskEntity update(AgentTaskEntity entity) {
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public AgentTaskEntity findById(Long id) {
            return null;
        }

        @Override
        public List<AgentTaskEntity> findByPlanId(Long planId) {
            return tasksByPlanId.getOrDefault(planId, Collections.emptyList());
        }

        @Override
        public List<AgentTaskEntity> findByPlanIdAndStatus(Long planId, TaskStatusEnum status) {
            return Collections.emptyList();
        }

        @Override
        public AgentTaskEntity findByPlanIdAndNodeId(Long planId, String nodeId) {
            return null;
        }

        @Override
        public List<AgentTaskEntity> findAll() {
            return Collections.emptyList();
        }

        @Override
        public List<AgentTaskEntity> findByStatus(TaskStatusEnum status) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentTaskEntity> findReadyTasks() {
            return Collections.emptyList();
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
