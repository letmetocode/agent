package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import com.getoffer.infrastructure.planning.WorkflowDraftLifecycleService;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.test.support.InMemoryWorkflowDraftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkflowDraftLifecycleServiceTest {

    @Test
    public void shouldReuseDraftByDedupHash() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        AtomicInteger plannerAttempts = new AtomicInteger(0);
        IRootWorkflowDraftPlanner rootPlanner = (sessionId, query, context) -> {
            plannerAttempts.incrementAndGet();
            return buildRootDraft(query, "assistant");
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WorkflowDraftLifecycleService service = new WorkflowDraftLifecycleService(
                    workflowDraftRepository,
                    rootPlanner,
                    agentRegistryRepository,
                    true,
                    "root",
                    3,
                    0L,
                    true,
                    "assistant",
                    1000L,
                    PlannerFallbackPolicyDomainService.defaultInstance(),
                    jsonCodec,
                    executor
            );

            WorkflowDraftEntity first = service.loadOrCreateDraft(1001L, "生成短视频文案", Collections.emptyMap());
            WorkflowDraftEntity second = service.loadOrCreateDraft(1002L, "生成短视频文案", Collections.emptyMap());

            Assertions.assertEquals(first.getId(), second.getId());
            Assertions.assertEquals(1, workflowDraftRepository.findAll().size());
            Assertions.assertEquals(2, plannerAttempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldFallbackWhenRootPlannerDisabled() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WorkflowDraftLifecycleService service = new WorkflowDraftLifecycleService(
                    workflowDraftRepository,
                    null,
                    agentRegistryRepository,
                    false,
                    "root",
                    3,
                    0L,
                    true,
                    "assistant",
                    1000L,
                    PlannerFallbackPolicyDomainService.defaultInstance(),
                    jsonCodec,
                    executor
            );

            WorkflowDraftEntity draft = service.loadOrCreateDraft(2001L, "unknown query", Collections.emptyMap());
            Assertions.assertEquals("AUTO_MISS_FALLBACK", draft.getSourceType());
            Assertions.assertEquals("ROOT_PLANNER_DISABLED", draft.getConstraints().get("fallbackReason"));
            Assertions.assertEquals(0, ((Number) draft.getConstraints().get("rootPlanningAttempts")).intValue());

            Map<String, Object> graphDefinition = draft.getGraphDefinition();
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphDefinition.get("nodes");
            Map<String, Object> node = nodes.get(0);
            Map<String, Object> config = (Map<String, Object>) node.get("config");
            Assertions.assertEquals("assistant", config.get("agentKey"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReplaceUnavailableAgentKeyDuringGraphNormalization() {
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());
        InMemoryWorkflowDraftRepository workflowDraftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryAgentRegistryRepository agentRegistryRepository = new InMemoryAgentRegistryRepository();
        agentRegistryRepository.save(agent("assistant", true));
        agentRegistryRepository.save(agent("offline-agent", false));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WorkflowDraftLifecycleService service = new WorkflowDraftLifecycleService(
                    workflowDraftRepository,
                    (sessionId, userQuery, context) -> buildRootDraft(userQuery, "assistant"),
                    agentRegistryRepository,
                    true,
                    "root",
                    3,
                    0L,
                    true,
                    "assistant",
                    1000L,
                    PlannerFallbackPolicyDomainService.defaultInstance(),
                    jsonCodec,
                    executor
            );

            Map<String, Object> graph = new HashMap<>();
            graph.put("version", 2);
            graph.put("groups", Collections.emptyList());
            graph.put("edges", Collections.emptyList());

            Map<String, Object> node = new HashMap<>();
            node.put("id", "worker-1");
            node.put("name", "worker-1");
            node.put("type", "WORKER");
            node.put("config", new HashMap<>(Map.of("agentKey", "offline-agent")));
            graph.put("nodes", Collections.singletonList(node));

            Map<String, Object> normalized = service.normalizeAndValidateGraphDefinition(graph, "候选草案", true);
            List<Map<String, Object>> normalizedNodes = (List<Map<String, Object>>) normalized.get("nodes");
            Map<String, Object> normalizedConfig = (Map<String, Object>) normalizedNodes.get(0).get("config");
            Assertions.assertEquals("assistant", normalizedConfig.get("agentKey"));
        } finally {
            executor.shutdownNow();
        }
    }

    private RootWorkflowDraft buildRootDraft(String userQuery, String agentKey) {
        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory("candidate");
        draft.setName("auto-" + userQuery);
        draft.setRouteDescription(userQuery);
        draft.setInputSchema(Collections.emptyMap());
        draft.setDefaultConfig(Map.of("priority", 0, "maxRetries", 3));
        draft.setToolPolicy(Map.of("mode", "restricted"));
        draft.setConstraints(Map.of("mode", "candidate-restricted"));
        draft.setInputSchemaVersion("v1");
        draft.setNodeSignature("sig-1");

        Map<String, Object> node = new HashMap<>();
        node.put("id", "worker-1");
        node.put("name", "worker-1");
        node.put("type", "WORKER");
        node.put("config", new HashMap<>(Map.of("agentKey", agentKey)));

        Map<String, Object> graph = new HashMap<>();
        graph.put("version", 2);
        graph.put("nodes", Collections.singletonList(node));
        graph.put("groups", Collections.emptyList());
        graph.put("edges", Collections.emptyList());
        draft.setGraphDefinition(graph);
        return draft;
    }

    private AgentRegistryEntity agent(String key, boolean active) {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setKey(key);
        entity.setName(key);
        entity.setModelProvider("openai");
        entity.setModelName("gpt-4.1");
        entity.setIsActive(active);
        return entity;
    }

    private static final class InMemoryAgentRegistryRepository implements IAgentRegistryRepository {

        private final Map<Long, AgentRegistryEntity> storeById = new LinkedHashMap<>();
        private final Map<String, AgentRegistryEntity> storeByKey = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public AgentRegistryEntity save(AgentRegistryEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            storeById.put(entity.getId(), entity);
            storeByKey.put(entity.getKey(), entity);
            return entity;
        }

        @Override
        public AgentRegistryEntity update(AgentRegistryEntity entity) {
            if (entity == null || entity.getId() == null) {
                return entity;
            }
            storeById.put(entity.getId(), entity);
            storeByKey.put(entity.getKey(), entity);
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            AgentRegistryEntity removed = storeById.remove(id);
            if (removed != null) {
                storeByKey.remove(removed.getKey());
            }
            return removed != null;
        }

        @Override
        public AgentRegistryEntity findById(Long id) {
            return storeById.get(id);
        }

        @Override
        public AgentRegistryEntity findByKey(String key) {
            return storeByKey.get(key);
        }

        @Override
        public List<AgentRegistryEntity> findAll() {
            return new ArrayList<>(storeById.values());
        }

        @Override
        public List<AgentRegistryEntity> findByActive(Boolean isActive) {
            List<AgentRegistryEntity> result = new ArrayList<>();
            for (AgentRegistryEntity item : storeById.values()) {
                if (isActive == null || isActive.equals(item.getIsActive())) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
            List<AgentRegistryEntity> result = new ArrayList<>();
            for (AgentRegistryEntity item : storeById.values()) {
                if (modelProvider == null || modelProvider.equals(item.getModelProvider())) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public boolean existsByKey(String key) {
            return storeByKey.containsKey(key);
        }
    }
}
