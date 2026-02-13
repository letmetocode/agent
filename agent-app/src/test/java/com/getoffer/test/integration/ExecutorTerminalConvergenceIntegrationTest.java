package com.getoffer.test.integration;

import com.getoffer.Application;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.task.scheduling.enabled=false",
                "executor.observability.audit-log-enabled=false",
                "executor.observability.audit-success-log-enabled=false"
        }
)
@EnabledIfSystemProperty(named = "it.docker.enabled", matches = "true")
public class ExecutorTerminalConvergenceIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private TurnFinalizeApplicationService turnResultService;

    @Autowired
    private ISessionTurnRepository sessionTurnRepository;

    @Autowired
    private ISessionMessageRepository sessionMessageRepository;

    @Autowired
    private IAgentTaskRepository agentTaskRepository;

    @Test
    public void shouldFinalizeToSingleAssistantMessageUnderConcurrentFinalize() throws Exception {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_session_messages_turn_assistant ON session_messages(turn_id, role) WHERE role = 'ASSISTANT'");

        Long sessionId = insertSession();
        Long planId = insertPlan(sessionId, "RUNNING");
        Long turnId = insertTurn(sessionId, planId, "EXECUTING");
        insertWorkerTask(planId, "node-1", "COMPLETED", "结果A");
        insertWorkerTask(planId, "node-2", "COMPLETED", "结果B");

        int concurrency = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<TurnFinalizeApplicationService.TurnFinalizeResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(new Callable<TurnFinalizeApplicationService.TurnFinalizeResult>() {
                    @Override
                    public TurnFinalizeApplicationService.TurnFinalizeResult call() throws Exception {
                        start.await(2, TimeUnit.SECONDS);
                        return turnResultService.finalizeByPlan(planId, PlanStatusEnum.COMPLETED);
                    }
                }));
            }
            start.countDown();

            int finalized = 0;
            int dedup = 0;
            int skipped = 0;
            for (Future<TurnFinalizeApplicationService.TurnFinalizeResult> future : futures) {
                TurnFinalizeApplicationService.TurnFinalizeResult result = future.get(8, TimeUnit.SECONDS);
                Assertions.assertNotNull(result);
                if (result.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.FINALIZED) {
                    finalized++;
                } else if (result.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED) {
                    dedup++;
                } else if (result.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.SKIPPED_NOT_TERMINAL) {
                    skipped++;
                }
            }

            SessionTurnEntity latestTurn = sessionTurnRepository.findByPlanId(planId);
            Assertions.assertNotNull(latestTurn);
            Assertions.assertEquals(TurnStatusEnum.COMPLETED, latestTurn.getStatus());
            Assertions.assertNotNull(latestTurn.getFinalResponseMessageId());

            List<SessionMessageEntity> messages = sessionMessageRepository.findByTurnId(turnId);
            long assistantMessageCount = messages.stream()
                    .filter(item -> item.getRole() == MessageRoleEnum.ASSISTANT)
                    .count();
            Assertions.assertEquals(1L, assistantMessageCount, "同一 turn 只允许一条 assistant 最终消息");
            Assertions.assertTrue(finalized >= 1, "至少应有一次真正 finalize");
            Assertions.assertEquals(concurrency, finalized + dedup + skipped);
            Assertions.assertEquals(0, skipped, "并发 finalize 不应出现 SKIPPED_NOT_TERMINAL");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void shouldRejectStaleClaimedWritebackInConcurrentReclaimScenario() throws Exception {
        Long sessionId = insertSession();
        Long planId = insertPlan(sessionId, "READY");
        int taskCount = 40;
        for (int i = 0; i < taskCount; i++) {
            insertWorkerTask(planId, "node-claim-" + i, "READY", null);
        }

        List<AgentTaskEntity> firstClaim = agentTaskRepository.claimReadyLikeTasks("owner-A", taskCount, 30);
        Assertions.assertEquals(taskCount, firstClaim.size(), "首轮 claim 应覆盖全部任务");

        jdbcTemplate.update(
                "UPDATE agent_tasks SET lease_until = CURRENT_TIMESTAMP - INTERVAL '5 seconds' WHERE plan_id = ?",
                planId
        );

        List<AgentTaskEntity> secondClaim = agentTaskRepository.claimReadyLikeTasks("owner-B", taskCount, 30);
        Assertions.assertEquals(taskCount, secondClaim.size(), "lease 过期后应可被二次重领");

        int staleSuccess = runClaimedWritebackConcurrently(firstClaim, "owner-A", "stale-result");
        Assertions.assertEquals(0, staleSuccess, "旧 owner + 旧 attempt 并发回写应全部被拒绝");

        int freshSuccess = runClaimedWritebackConcurrently(secondClaim, "owner-B", "fresh-result");
        Assertions.assertEquals(taskCount, freshSuccess, "当前 owner + attempt 并发回写应全部成功");

        List<AgentTaskEntity> stored = agentTaskRepository.findByPlanId(planId);
        long completedCount = stored.stream().filter(item -> item.getStatus() == TaskStatusEnum.COMPLETED).count();
        long releasedCount = stored.stream().filter(item -> item.getClaimOwner() == null).count();
        Assertions.assertEquals(taskCount, completedCount);
        Assertions.assertEquals(taskCount, releasedCount);
    }

    private int runClaimedWritebackConcurrently(List<AgentTaskEntity> claimedTasks,
                                                String expectedOwner,
                                                String resultPrefix) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (AgentTaskEntity claimed : claimedTasks) {
                futures.add(pool.submit(() -> {
                    AgentTaskEntity update = new AgentTaskEntity();
                    update.setId(claimed.getId());
                    update.setClaimOwner(expectedOwner);
                    update.setExecutionAttempt(claimed.getExecutionAttempt());
                    update.setStatus(TaskStatusEnum.COMPLETED);
                    update.setCurrentRetry(0);
                    update.setInputContext(new HashMap<>());
                    update.setOutputResult(resultPrefix + "-" + claimed.getId());
                    return agentTaskRepository.updateClaimedTaskState(update);
                }));
            }
            int success = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get(8, TimeUnit.SECONDS))) {
                    success++;
                }
            }
            return success;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long insertSession() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent_sessions(user_id, title, is_active, meta_info, created_at) VALUES (?, ?, TRUE, '{}'::jsonb, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                "it-user",
                "it-session"
        );
    }

    private Long insertPlan(Long sessionId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent_plans(session_id, route_decision_id, plan_goal, execution_graph, definition_snapshot, global_context, status, priority, version, created_at, updated_at) " +
                        "VALUES (?, 1, ?, '{\"nodes\":[]}'::jsonb, '{\"source\":\"IT\"}'::jsonb, '{}'::jsonb, ?::plan_status_enum, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                sessionId,
                "it-plan-" + status,
                status
        );
    }

    private Long insertTurn(Long sessionId, Long planId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO session_turns(session_id, plan_id, user_message, status, metadata, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?::turn_status_enum, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                sessionId,
                planId,
                "integration-message",
                status
        );
    }

    private Long insertWorkerTask(Long planId, String nodeId, String status, String outputResult) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO agent_tasks(plan_id, node_id, name, task_type, status, dependency_node_ids, input_context, config_snapshot, output_result, max_retries, current_retry, execution_attempt, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'WORKER'::task_type_enum, ?::task_status_enum, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, ?, 3, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                planId,
                nodeId,
                nodeId,
                status,
                outputResult
        );
    }
}
