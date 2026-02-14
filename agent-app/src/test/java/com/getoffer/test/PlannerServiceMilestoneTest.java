package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.infrastructure.planning.PlannerServiceImpl;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.test.support.InMemoryRoutingDecisionRepository;
import com.getoffer.test.support.InMemoryWorkflowDefinitionRepository;
import com.getoffer.test.support.InMemoryWorkflowDraftRepository;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlannerServiceMilestoneTest {

    @Test
    public void shouldCreatePlanAndTasksWithDependencies() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();

        WorkflowDefinitionEntity definition = buildSnakeGameDefinition();
        workflowDefinitionRepository.save(definition);

        PlannerService plannerService = new PlannerServiceImpl(
                workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec
        );

        Long sessionId = 1001L;
        String userQuery = "帮我写个贪吃蛇游戏";

        AgentPlanEntity plan = plannerService.createPlan(sessionId, userQuery);

        Assert.assertNotNull(plan);
        Assert.assertNotNull(plan.getId());
        Assert.assertEquals(sessionId, plan.getSessionId());
        Assert.assertEquals(userQuery, plan.getPlanGoal());
        Assert.assertEquals(PlanStatusEnum.READY, plan.getStatus());

        List<AgentPlanEntity> storedPlans = agentPlanRepository.findAll();
        Assert.assertEquals(1, storedPlans.size());
        Assert.assertEquals(plan.getId(), storedPlans.get(0).getId());

        List<AgentTaskEntity> storedTasks = agentTaskRepository.findByPlanId(plan.getId());
        Assert.assertEquals(4, storedTasks.size());

        Map<String, AgentTaskEntity> taskByNode = storedTasks.stream()
                .collect(Collectors.toMap(AgentTaskEntity::getNodeId, task -> task));

        assertDependencies(taskByNode, "analysis", Collections.emptySet());
        assertDependencies(taskByNode, "design", Set.of("analysis"));
        assertDependencies(taskByNode, "implement", Set.of("design"));
        assertDependencies(taskByNode, "review", Set.of("implement"));

        for (AgentTaskEntity task : storedTasks) {
            Assert.assertEquals(plan.getId(), task.getPlanId());
            Assert.assertEquals(TaskStatusEnum.PENDING, task.getStatus());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseExecutionGraphAsSingleExecutionSource() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();

        WorkflowDefinitionEntity definition = buildSnakeGameDefinition();
        workflowDefinitionRepository.save(definition);

        PlannerService plannerService = new PlannerServiceImpl(
                workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec
        );

        AgentPlanEntity plan = plannerService.createPlan(1010L, "帮我写个贪吃蛇游戏");

        Assert.assertNotNull(plan.getRouteDecisionId());
        Assert.assertNotNull(routingDecisionRepository.findById(plan.getRouteDecisionId()));

        Map<String, Object> executionGraph = plan.getExecutionGraph();
        Assert.assertNotNull(executionGraph);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) executionGraph.get("nodes");
        Assert.assertNotNull(nodes);
        Assert.assertFalse(nodes.isEmpty());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assert.assertEquals(nodes.size(), tasks.size());

        Map<String, Object> definitionSnapshot = plan.getDefinitionSnapshot();
        Assert.assertNotNull(definitionSnapshot);
        Assert.assertEquals("HIT_PRODUCTION", String.valueOf(definitionSnapshot.get("routeType")));
        Assert.assertFalse("definitionSnapshot 不应包含执行图，避免双事实源",
                definitionSnapshot.containsKey("graphDefinition"));
    }

    @Test
    public void shouldUseRootDraftWhenNoProductionDefinitionMatched() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger rootAttempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, userQuery, context) -> {
            rootAttempts.incrementAndGet();
            return buildRootDraft(userQuery);
        };

        PlannerService plannerService = new PlannerServiceImpl(
                workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                rootPlanner,
                agentRegistryRepository,
                true,
                "root",
                3,
                0L,
                true,
                "assistant"
        );

        AgentPlanEntity plan = plannerService.createPlan(2001L, "帮我制定一个需求分析到实现的流程");
        Assert.assertNotNull(plan);
        Assert.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assert.assertEquals(1, rootAttempts.get());

        List<WorkflowDraftEntity> drafts = workflowDraftRepository.findAll();
        Assert.assertEquals(1, drafts.size());
        WorkflowDraftEntity draft = drafts.get(0);
        Assert.assertEquals(WorkflowDraftStatusEnum.DRAFT, draft.getStatus());
        Assert.assertEquals("AUTO_MISS_ROOT", draft.getSourceType());
        Assert.assertNotNull(draft.getDedupHash());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assert.assertEquals(2, tasks.size());
        for (AgentTaskEntity task : tasks) {
            Assert.assertEquals(TaskStatusEnum.PENDING, task.getStatus());
            Assert.assertEquals("assistant", task.getConfigSnapshot().get("agentKey"));
        }
    }

    @Test
    public void shouldFallbackSingleNodeAfterRootPlanningRetriedThreeTimes() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger rootAttempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, userQuery, context) -> {
            rootAttempts.incrementAndGet();
            throw new IllegalStateException("root planning failed");
        };

        PlannerService plannerService = new PlannerServiceImpl(
                workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                rootPlanner,
                agentRegistryRepository,
                true,
                "root",
                3,
                0L,
                true,
                "assistant"
        );

        AgentPlanEntity plan = plannerService.createPlan(2002L, "帮我处理一个未知流程问题");
        Assert.assertNotNull(plan);
        Assert.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assert.assertEquals(3, rootAttempts.get());

        List<WorkflowDraftEntity> drafts = workflowDraftRepository.findAll();
        Assert.assertEquals(1, drafts.size());
        WorkflowDraftEntity draft = drafts.get(0);
        Assert.assertEquals("AUTO_MISS_FALLBACK", draft.getSourceType());
        Assert.assertEquals(WorkflowDraftStatusEnum.DRAFT, draft.getStatus());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assert.assertEquals(1, tasks.size());
        AgentTaskEntity fallbackTask = tasks.get(0);
        Assert.assertEquals("candidate-worker", fallbackTask.getNodeId());
        Assert.assertEquals("assistant", fallbackTask.getConfigSnapshot().get("agentKey"));
    }

    private void assertDependencies(Map<String, AgentTaskEntity> taskByNode,
                                    String nodeId,
                                    Set<String> expected) {
        AgentTaskEntity task = taskByNode.get(nodeId);
        Assert.assertNotNull("Missing task for node " + nodeId, task);
        List<String> deps = task.getDependencyNodeIds();
        Assert.assertNotNull("Dependency list must not be null", deps);
        Assert.assertEquals(expected.size(), deps.size());
        Assert.assertTrue("Dependency mismatch for node " + nodeId, deps.containsAll(expected));
    }

    private WorkflowDefinitionEntity buildSnakeGameDefinition() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(node("analysis", "需求分析", "WORKER"));
        nodes.add(node("design", "方案设计", "WORKER"));
        nodes.add(node("implement", "编码实现", "WORKER"));
        nodes.add(node("review", "质量评审", "CRITIC"));

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(edge("analysis", "design"));
        edges.add(edge("design", "implement"));
        edges.add(edge("implement", "review"));

        Map<String, Object> graph = new HashMap<>();
        graph.put("version", 2);
        graph.put("groups", Collections.emptyList());
        graph.put("nodes", nodes);
        graph.put("edges", edges);

        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setId(1L);
        definition.setCategory("coding");
        definition.setName("snake-game");
        definition.setDefinitionKey("coding-snake-game");
        definition.setTenantId("DEFAULT");
        definition.setVersion(1);
        definition.setRouteDescription("帮我写个贪吃蛇游戏");
        definition.setStatus(WorkflowDefinitionStatusEnum.ACTIVE);
        definition.setGraphDefinition(graph);
        definition.setInputSchema(Collections.emptyMap());
        definition.setDefaultConfig(Collections.singletonMap("priority", 1));
        definition.setIsActive(true);
        return definition;
    }

    private RootWorkflowDraft buildRootDraft(String userQuery) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("root-" + userQuery);
        draft.setRouteDescription(userQuery);
        draft.setInputSchema(Collections.emptyMap());
        draft.setDefaultConfig(Collections.singletonMap("maxRetries", 2));
        draft.setToolPolicy(Collections.singletonMap("mode", "restricted"));
        draft.setConstraints(Collections.singletonMap("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> n1 = new HashMap<>();
        n1.put("id", "analysis");
        n1.put("name", "分析");
        n1.put("type", "WORKER");
        n1.put("config", new HashMap<>());
        nodes.add(n1);

        Map<String, Object> n2 = new HashMap<>();
        n2.put("id", "implement");
        n2.put("name", "实现");
        n2.put("type", "WORKER");
        n2.put("config", new HashMap<>());
        nodes.add(n2);

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(edge("analysis", "implement"));

        Map<String, Object> graph = new HashMap<>();
        graph.put("version", 2);
        graph.put("groups", Collections.emptyList());
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        draft.setGraphDefinition(graph);
        return draft;
    }

    private AgentRegistryEntity agent(String key, boolean active) {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setKey(key);
        entity.setName(key);
        entity.setModelProvider("openai");
        entity.setModelName("gpt-4");
        entity.setIsActive(active);
        entity.setModelOptions(Collections.emptyMap());
        entity.setAdvisorConfig(Collections.emptyMap());
        return entity;
    }

    private Map<String, Object> node(String id, String name, String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", type);
        return node;
    }

    private Map<String, Object> edge(String from, String to) {
        Map<String, Object> edge = new HashMap<>();
        edge.put("from", from);
        edge.put("to", to);
        return edge;
    }

    private static final class InMemoryAgentPlanRepository implements IAgentPlanRepository {

        private final Map<Long, AgentPlanEntity> store = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public AgentPlanEntity save(AgentPlanEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public AgentPlanEntity update(AgentPlanEntity entity) {
            if (entity == null || entity.getId() == null) {
                return entity;
            }
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
                    .filter(plan -> plan.getStatus() == PlanStatusEnum.READY
                            || plan.getStatus() == PlanStatusEnum.RUNNING)
                    .collect(Collectors.toList());
        }
    }

    private static final class InMemoryAgentTaskRepository implements IAgentTaskRepository {

        private final Map<Long, AgentTaskEntity> store = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public AgentTaskEntity save(AgentTaskEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public AgentTaskEntity update(AgentTaskEntity entity) {
            if (entity == null || entity.getId() == null) {
                return entity;
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
        public List<AgentTaskEntity> claimReadyLikeTasks(String claimOwner, int limit, int leaseSeconds) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentTaskEntity> claimRefiningTasks(String claimOwner, int limit, int leaseSeconds) {
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
            if (entities == null) {
                return Collections.emptyList();
            }
            List<AgentTaskEntity> saved = new ArrayList<>();
            for (AgentTaskEntity entity : entities) {
                saved.add(save(entity));
            }
            return saved;
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

    private static final class InMemoryAgentRegistryRepository implements IAgentRegistryRepository {

        private final Map<Long, AgentRegistryEntity> byId = new LinkedHashMap<>();
        private final Map<String, AgentRegistryEntity> byKey = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public AgentRegistryEntity save(AgentRegistryEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            byId.put(entity.getId(), entity);
            byKey.put(entity.getKey(), entity);
            return entity;
        }

        @Override
        public AgentRegistryEntity update(AgentRegistryEntity entity) {
            if (entity == null || entity.getId() == null) {
                return entity;
            }
            byId.put(entity.getId(), entity);
            if (entity.getKey() != null) {
                byKey.put(entity.getKey(), entity);
            }
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            AgentRegistryEntity removed = byId.remove(id);
            if (removed != null && removed.getKey() != null) {
                byKey.remove(removed.getKey());
            }
            return removed != null;
        }

        @Override
        public AgentRegistryEntity findById(Long id) {
            return byId.get(id);
        }

        @Override
        public AgentRegistryEntity findByKey(String key) {
            return byKey.get(key);
        }

        @Override
        public List<AgentRegistryEntity> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRegistryEntity> findByActive(Boolean isActive) {
            return byId.values().stream()
                    .filter(item -> Objects.equals(isActive, item.getIsActive()))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByKey(String key) {
            return byKey.containsKey(key);
        }
    }
}
