package com.getoffer.test.integration;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.Application;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class TaskClaimRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private IAgentTaskRepository agentTaskRepository;

    @Autowired
    private IAgentPlanRepository agentPlanRepository;

    @Test
    public void shouldClaimTasksMutuallyExclusiveAcrossConcurrentConsumers() throws Exception {
        AgentPlanEntity plan = savePlan(PlanStatusEnum.READY);
        for (int i = 0; i < 20; i++) {
            saveTask(plan.getId(), "node-ready-" + i, TaskStatusEnum.READY);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<List<AgentTaskEntity>> c1 = () -> {
                start.await(2, TimeUnit.SECONDS);
                return agentTaskRepository.claimReadyLikeTasks("owner-A", 20, 30);
            };
            Callable<List<AgentTaskEntity>> c2 = () -> {
                start.await(2, TimeUnit.SECONDS);
                return agentTaskRepository.claimReadyLikeTasks("owner-B", 20, 30);
            };

            Future<List<AgentTaskEntity>> f1 = pool.submit(c1);
            Future<List<AgentTaskEntity>> f2 = pool.submit(c2);
            start.countDown();

            List<AgentTaskEntity> result1 = safeList(f1.get(5, TimeUnit.SECONDS));
            List<AgentTaskEntity> result2 = safeList(f2.get(5, TimeUnit.SECONDS));

            Set<Long> ids1 = toIdSet(result1);
            Set<Long> ids2 = toIdSet(result2);
            Set<Long> overlap = new HashSet<>(ids1);
            overlap.retainAll(ids2);

            Assertions.assertTrue(overlap.isEmpty(), "并发 claim 不应领取到相同 task");
            Assertions.assertEquals(20, ids1.size() + ids2.size(), "20 个 READY 任务应被本轮全部领取");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void shouldReclaimExpiredLeaseAndBlockStaleAttemptWriteback() {
        AgentPlanEntity plan = savePlan(PlanStatusEnum.READY);
        AgentTaskEntity task = saveTask(plan.getId(), "node-reclaim", TaskStatusEnum.READY);

        List<AgentTaskEntity> firstClaim = agentTaskRepository.claimReadyLikeTasks("owner-A", 1, 30);
        Assertions.assertEquals(1, firstClaim.size(), "首次 claim 应成功");
        AgentTaskEntity claimedA = firstClaim.get(0);
        Assertions.assertEquals(task.getId(), claimedA.getId());
        Assertions.assertEquals(1, claimedA.getExecutionAttempt());

        jdbcTemplate.update(
                "UPDATE agent_tasks SET lease_until = CURRENT_TIMESTAMP - INTERVAL '10 seconds' WHERE id = ?",
                claimedA.getId()
        );

        List<AgentTaskEntity> secondClaim = agentTaskRepository.claimReadyLikeTasks("owner-B", 1, 30);
        Assertions.assertEquals(1, secondClaim.size(), "lease 过期后应可重领");
        AgentTaskEntity claimedB = secondClaim.get(0);
        Assertions.assertEquals(task.getId(), claimedB.getId());
        Assertions.assertEquals(2, claimedB.getExecutionAttempt());

        AgentTaskEntity staleWrite = new AgentTaskEntity();
        staleWrite.setId(task.getId());
        staleWrite.setClaimOwner("owner-A");
        staleWrite.setExecutionAttempt(1);
        staleWrite.setStatus(TaskStatusEnum.COMPLETED);
        staleWrite.setCurrentRetry(0);
        staleWrite.setInputContext(new HashMap<>());
        staleWrite.setOutputResult("stale-result");
        boolean staleUpdated = agentTaskRepository.updateClaimedTaskState(staleWrite);
        Assertions.assertFalse(staleUpdated, "旧 attempt 回写应被拒绝");

        AgentTaskEntity freshWrite = new AgentTaskEntity();
        freshWrite.setId(task.getId());
        freshWrite.setClaimOwner("owner-B");
        freshWrite.setExecutionAttempt(2);
        freshWrite.setStatus(TaskStatusEnum.COMPLETED);
        freshWrite.setCurrentRetry(0);
        freshWrite.setInputContext(new HashMap<>());
        freshWrite.setOutputResult("fresh-result");
        boolean freshUpdated = agentTaskRepository.updateClaimedTaskState(freshWrite);
        Assertions.assertTrue(freshUpdated, "当前 claim owner + attempt 回写应成功");

        AgentTaskEntity stored = agentTaskRepository.findById(task.getId());
        Assertions.assertEquals(TaskStatusEnum.COMPLETED, stored.getStatus());
        Assertions.assertNull(stored.getClaimOwner(), "终态回写后 claim_owner 应清空");
    }

    @Test
    public void shouldNotClaimTasksWhenPlanIsPaused() {
        AgentPlanEntity plan = savePlan(PlanStatusEnum.PAUSED);
        AgentTaskEntity task = saveTask(plan.getId(), "node-paused", TaskStatusEnum.READY);

        List<AgentTaskEntity> claimed = agentTaskRepository.claimReadyLikeTasks("owner-A", 10, 30);
        Assertions.assertTrue(claimed.isEmpty(), "PAUSED Plan 下任务不应被 claim");

        AgentTaskEntity stored = agentTaskRepository.findById(task.getId());
        Assertions.assertEquals(TaskStatusEnum.READY, stored.getStatus());
    }

    private AgentPlanEntity savePlan(PlanStatusEnum status) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setSessionId(1L);
        plan.setRouteDecisionId(1L);
        plan.setPlanGoal("it-plan-" + status.name());
        plan.setExecutionGraph(Collections.singletonMap("nodes", Collections.emptyList()));
        plan.setDefinitionSnapshot(Collections.singletonMap("routeType", "IT_TEST"));
        plan.setGlobalContext(new HashMap<>());
        plan.setStatus(status);
        plan.setPriority(0);
        plan.setVersion(0);
        return agentPlanRepository.save(plan);
    }

    private AgentTaskEntity saveTask(Long planId, String nodeId, TaskStatusEnum status) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setPlanId(planId);
        task.setNodeId(nodeId);
        task.setName(nodeId);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setStatus(status);
        task.setDependencyNodeIds(new ArrayList<>());
        task.setInputContext(new HashMap<>());
        task.setConfigSnapshot(new HashMap<>());
        task.setMaxRetries(3);
        task.setCurrentRetry(0);
        task.setExecutionAttempt(0);
        task.setVersion(0);
        return agentTaskRepository.save(task);
    }

    private Set<Long> toIdSet(List<AgentTaskEntity> tasks) {
        Set<Long> ids = new HashSet<>();
        for (AgentTaskEntity task : tasks) {
            if (task != null && task.getId() != null) {
                ids.add(task.getId());
            }
        }
        return ids;
    }

    private List<AgentTaskEntity> safeList(List<AgentTaskEntity> tasks) {
        return tasks == null ? Collections.emptyList() : tasks;
    }
}
