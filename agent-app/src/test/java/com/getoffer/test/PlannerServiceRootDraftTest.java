package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
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
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlannerServiceRootDraftTest {

    @Test
    public void shouldGenerateCandidateViaRootPlanner() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger attempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
            attempts.incrementAndGet();
            return buildDraft(query);
        };

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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

        AgentPlanEntity plan = plannerService.createPlan(5001L, "未命中Workflow Definition场景");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals(1, attempts.get());

        WorkflowDraftEntity draft = workflowDraftRepository.findAll().get(0);
        Assertions.assertEquals(WorkflowDraftStatusEnum.DRAFT, draft.getStatus());
        Assertions.assertEquals("AUTO_MISS_ROOT", draft.getSourceType());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(2, tasks.size());
        for (AgentTaskEntity task : tasks) {
            Assertions.assertEquals("assistant", task.getConfigSnapshot().get("agentKey"));
        }
    }

    @Test
    public void shouldFallbackAfterThreeRootPlannerFailures() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger attempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("root failed");
        };

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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

        AgentPlanEntity plan = plannerService.createPlan(5002L, "root失败降级");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals(3, attempts.get());

        WorkflowDraftEntity draft = workflowDraftRepository.findAll().get(0);
        Assertions.assertEquals("AUTO_MISS_FALLBACK", draft.getSourceType());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals("candidate-worker", tasks.get(0).getNodeId());
        Assertions.assertEquals("assistant", tasks.get(0).getConfigSnapshot().get("agentKey"));
    }

    @Test
    public void shouldRecordPlannerRouteAndFallbackMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
            InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
            InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
            InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
            InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
            InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
            InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
            agentRegistryRepository.save(agent("assistant", true));

            IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
                throw new IllegalStateException("root failed");
            };

            PlannerServiceImpl plannerService = new PlannerServiceImpl(
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

            AgentPlanEntity plan = plannerService.createPlan(5006L, "root失败触发指标");
            Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());

            double routeFallback = registry.get("agent.planner.route.total")
                    .tag("decision_type", "FALLBACK")
                    .counter()
                    .count();
            double fallbackCount = registry.get("agent.planner.fallback.total")
                    .tag("reason", "ROOT_PLANNING_FAILED")
                    .counter()
                    .count();
            Assertions.assertEquals(1D, routeFallback, 0.0001D);
            Assertions.assertEquals(1D, fallbackCount, 0.0001D);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    @Test
    public void shouldAutoFallbackToRootWhenFallbackAgentKeyMissing() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("root", true));

        AtomicInteger attempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("root failed");
        };

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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

        AgentPlanEntity plan = plannerService.createPlan(5003L, "fallback agent key missing");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals(3, attempts.get());

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals("root", tasks.get(0).getConfigSnapshot().get("agentKey"));
    }

    @Test
    public void shouldExtractProductNameFromNaturalLanguageWhenRequiredBySchema() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("root", true));

        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> buildDraftWithRequiredProductName(query);

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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
                "root"
        );

        AgentPlanEntity plan = plannerService.createPlan(5004L, "为我生成一个小红书商品推荐文案，商品名为倍轻松back2f");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals("倍轻松back2f", plan.getGlobalContext().get("productName"));

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals("倍轻松back2f", tasks.get(0).getInputContext().get("productName"));
    }

    @Test
    public void shouldInjectSessionIdWhenInputSchemaRequiresIt() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("root", true));

        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> buildDraftWithRequiredSessionId(query);

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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
                "root"
        );

        Long sessionId = 5010L;
        AgentPlanEntity plan = plannerService.createPlan(sessionId, "你好");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals(sessionId, plan.getGlobalContext().get("sessionId"));

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals(sessionId, tasks.get(0).getInputContext().get("sessionId"));
    }

    @Test
    public void shouldRecordPlannerRouteMetricsForProductionHit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
            InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
            InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
            InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
            InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
            InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
            InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
            agentRegistryRepository.save(agent("assistant", true));

            WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
            definition.setDefinitionKey("metric-prod-hit");
            definition.setTenantId("DEFAULT");
            definition.setCategory("IT");
            definition.setName("metric-prod-hit");
            definition.setVersion(1);
            definition.setRouteDescription("生产命中指标测试");
            definition.setStatus(WorkflowDefinitionStatusEnum.ACTIVE);
            definition.setIsActive(true);
            Map<String, Object> graph = new HashMap<>();
            graph.put("nodes", List.of(new HashMap<>(Map.of("id", "node-1", "type", "WORKER", "name", "worker-1"))));
            graph.put("edges", new ArrayList<>());
            definition.setGraphDefinition(graph);
            definition.setInputSchema(new HashMap<>());
            definition.setDefaultConfig(new HashMap<>());
            workflowDefinitionRepository.save(definition);

            PlannerServiceImpl plannerService = new PlannerServiceImpl(
                    workflowDefinitionRepository,
                    workflowDraftRepository,
                    routingDecisionRepository,
                    agentPlanRepository,
                    agentTaskRepository,
                    jsonCodec,
                    null,
                    agentRegistryRepository,
                    true,
                    "root",
                    3,
                    0L,
                    true,
                    "assistant"
            );

            AgentPlanEntity plan = plannerService.createPlan(5007L, "生产命中指标测试");
            Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());

            double productionRoute = registry.get("agent.planner.route.total")
                    .tag("decision_type", "HIT_PRODUCTION")
                    .counter()
                    .count();
            Assertions.assertEquals(1D, productionRoute, 0.0001D);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldIgnoreVirtualBoundaryEdgesInRootDraft() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository();
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryRoutingDecisionRepository routingDecisionRepository = new InMemoryRoutingDecisionRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger attempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
            attempts.incrementAndGet();
            return buildDraftWithVirtualBoundaryEdges(query);
        };

        PlannerServiceImpl plannerService = new PlannerServiceImpl(
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

        AgentPlanEntity plan = plannerService.createPlan(5005L, "未命中Workflow Definition且含边界节点");
        Assertions.assertEquals(PlanStatusEnum.READY, plan.getStatus());
        Assertions.assertEquals(1, attempts.get());

        WorkflowDraftEntity draft = workflowDraftRepository.findAll().get(0);
        Map<String, Object> graph = draft.getGraphDefinition();
        List<Map<String, Object>> normalizedEdges = (List<Map<String, Object>>) graph.get("edges");
        Assertions.assertEquals(1, normalizedEdges.size());
        Assertions.assertEquals("1", normalizedEdges.get(0).get("from"));
        Assertions.assertEquals("2", normalizedEdges.get(0).get("to"));

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(plan.getId());
        Assertions.assertEquals(2, tasks.size());
        Map<String, AgentTaskEntity> byNodeId = tasks.stream().collect(Collectors.toMap(AgentTaskEntity::getNodeId, it -> it));
        Assertions.assertTrue(byNodeId.get("1").getDependencyNodeIds().isEmpty());
        Assertions.assertEquals(Collections.singletonList("1"), byNodeId.get("2").getDependencyNodeIds());
    }

    private RootWorkflowDraft buildDraft(String query) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("root-" + query);
        draft.setRouteDescription(query);
        draft.setInputSchema(Collections.emptyMap());
        draft.setDefaultConfig(Collections.singletonMap("maxRetries", 2));
        draft.setToolPolicy(Collections.singletonMap("mode", "restricted"));
        draft.setConstraints(Collections.singletonMap("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> node1 = new HashMap<>();
        node1.put("id", "analysis");
        node1.put("name", "分析");
        node1.put("type", "WORKER");
        node1.put("config", new HashMap<>());
        nodes.add(node1);

        Map<String, Object> node2 = new HashMap<>();
        node2.put("id", "implement");
        node2.put("name", "实现");
        node2.put("type", "WORKER");
        node2.put("config", new HashMap<>());
        nodes.add(node2);

        Map<String, Object> edge = new HashMap<>();
        edge.put("from", "analysis");
        edge.put("to", "implement");

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", Collections.singletonList(edge));
        draft.setGraphDefinition(graph);
        return draft;
    }

    private RootWorkflowDraft buildDraftWithRequiredProductName(String query) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("root-product-required-" + query);
        draft.setRouteDescription(query);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> productNameSchema = new HashMap<>();
        productNameSchema.put("type", "string");
        productNameSchema.put("description", "商品名称，必填");
        properties.put("productName", productNameSchema);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList("productName"));
        draft.setInputSchema(inputSchema);

        draft.setDefaultConfig(Collections.singletonMap("maxRetries", 2));
        draft.setToolPolicy(Collections.singletonMap("mode", "restricted"));
        draft.setConstraints(Collections.singletonMap("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");

        Map<String, Object> node = new HashMap<>();
        node.put("id", "candidate-worker");
        node.put("name", "执行");
        node.put("type", "WORKER");
        node.put("config", new HashMap<>());

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", Collections.singletonList(node));
        graph.put("edges", Collections.emptyList());
        draft.setGraphDefinition(graph);
        return draft;
    }

    private RootWorkflowDraft buildDraftWithRequiredSessionId(String query) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("root-session-required-" + query);
        draft.setRouteDescription(query);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> sessionIdSchema = new HashMap<>();
        sessionIdSchema.put("type", "integer");
        sessionIdSchema.put("description", "会话 ID，由系统注入");
        properties.put("sessionId", sessionIdSchema);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList("sessionId"));
        draft.setInputSchema(inputSchema);

        draft.setDefaultConfig(Collections.singletonMap("maxRetries", 2));
        draft.setToolPolicy(Collections.singletonMap("mode", "restricted"));
        draft.setConstraints(Collections.singletonMap("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");

        Map<String, Object> node = new HashMap<>();
        node.put("id", "candidate-worker");
        node.put("name", "执行");
        node.put("type", "WORKER");
        node.put("config", new HashMap<>());

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", Collections.singletonList(node));
        graph.put("edges", Collections.emptyList());
        draft.setGraphDefinition(graph);
        return draft;
    }

    private RootWorkflowDraft buildDraftWithVirtualBoundaryEdges(String query) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("root-virtual-boundary-" + query);
        draft.setRouteDescription(query);
        draft.setInputSchema(Collections.emptyMap());
        draft.setDefaultConfig(Collections.singletonMap("maxRetries", 2));
        draft.setToolPolicy(Collections.singletonMap("mode", "restricted"));
        draft.setConstraints(Collections.singletonMap("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");

        Map<String, Object> node1 = new HashMap<>();
        node1.put("id", "1");
        node1.put("name", "步骤1");
        node1.put("type", "WORKER");
        node1.put("config", new HashMap<>());

        Map<String, Object> node2 = new HashMap<>();
        node2.put("id", "2");
        node2.put("name", "步骤2");
        node2.put("type", "WORKER");
        node2.put("config", new HashMap<>());

        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, Object> edge1 = new HashMap<>();
        edge1.put("from", "START");
        edge1.put("to", "1");
        edges.add(edge1);

        Map<String, Object> edge2 = new HashMap<>();
        edge2.put("from", "1");
        edge2.put("to", "2");
        edges.add(edge2);

        Map<String, Object> edge3 = new HashMap<>();
        edge3.put("from", "2");
        edge3.put("to", "END");
        edges.add(edge3);

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", List.of(node1, node2));
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
        entity.setModelOptions(Collections.emptyMap());
        entity.setAdvisorConfig(Collections.emptyMap());
        entity.setIsActive(active);
        return entity;
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
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public AgentPlanEntity findById(Long id) {
            return store.get(id);
        }

        @Override
        public List<AgentPlanEntity> findBySessionId(Long sessionId) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentPlanEntity> findByStatus(PlanStatusEnum status) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentPlanEntity> findByStatusPaged(PlanStatusEnum status, int offset, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentPlanEntity> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<AgentPlanEntity> findByWorkflowDefinitionId(Long workflowDefinitionId) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentPlanEntity> findExecutablePlans() {
            return Collections.emptyList();
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
            return store.values().stream()
                    .filter(item -> Objects.equals(planId, item.getPlanId()))
                    .collect(Collectors.toList());
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
            return new ArrayList<>(store.values());
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
            return 0L;
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
