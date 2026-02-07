package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.ISopTemplateRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.SopTemplateEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.infrastructure.planning.PlannerServiceImpl;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.SopStructureEnum;
import com.getoffer.types.enums.TaskStatusEnum;
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
import java.util.stream.Collectors;

public class PlannerServiceMilestoneTest {

    @Test
    public void shouldCreatePlanAndTasksWithDependencies() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        InMemorySopTemplateRepository sopTemplateRepository = new InMemorySopTemplateRepository();
        InMemoryAgentPlanRepository agentPlanRepository = new InMemoryAgentPlanRepository();
        InMemoryAgentTaskRepository agentTaskRepository = new InMemoryAgentTaskRepository();

        SopTemplateEntity template = buildSnakeGameTemplate();
        sopTemplateRepository.save(template);

        PlannerService plannerService = new PlannerServiceImpl(
                sopTemplateRepository,
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

    private SopTemplateEntity buildSnakeGameTemplate() {
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
        graph.put("nodes", nodes);
        graph.put("edges", edges);

        SopTemplateEntity template = new SopTemplateEntity();
        template.setId(1L);
        template.setCategory("coding");
        template.setName("snake-game");
        template.setVersion(1);
        template.setTriggerDescription("帮我写个贪吃蛇游戏");
        template.setStructureType(SopStructureEnum.DAG);
        template.setGraphDefinition(graph);
        template.setInputSchema(Collections.emptyMap());
        template.setDefaultConfig(Collections.singletonMap("priority", 1));
        template.setIsActive(true);
        return template;
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

    private static final class InMemorySopTemplateRepository implements ISopTemplateRepository {

        private final Map<Long, SopTemplateEntity> store = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public SopTemplateEntity save(SopTemplateEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public SopTemplateEntity update(SopTemplateEntity entity) {
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
        public SopTemplateEntity findById(Long id) {
            return store.get(id);
        }

        @Override
        public SopTemplateEntity findByCategoryAndNameAndVersion(String category, String name, Integer version) {
            return store.values().stream()
                    .filter(template -> Objects.equals(category, template.getCategory()))
                    .filter(template -> Objects.equals(name, template.getName()))
                    .filter(template -> Objects.equals(version, template.getVersion()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<SopTemplateEntity> findByCategory(String category) {
            return store.values().stream()
                    .filter(template -> Objects.equals(category, template.getCategory()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SopTemplateEntity> findByCategoryAndName(String category, String name) {
            return store.values().stream()
                    .filter(template -> Objects.equals(category, template.getCategory()))
                    .filter(template -> Objects.equals(name, template.getName()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SopTemplateEntity> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<SopTemplateEntity> findByActive(Boolean isActive) {
            if (isActive == null) {
                return new ArrayList<>(store.values());
            }
            return store.values().stream()
                    .filter(template -> Objects.equals(isActive, template.getIsActive()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SopTemplateEntity> findByStructureType(String structureType) {
            return store.values().stream()
                    .filter(template -> template.getStructureType() != null)
                    .filter(template -> Objects.equals(structureType, template.getStructureType().getCode()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<SopTemplateEntity> searchByTriggerDescription(String keyword) {
            if (keyword == null || keyword.isEmpty()) {
                return new ArrayList<>(store.values());
            }
            return store.values().stream()
                    .filter(template -> template.getTriggerDescription() != null)
                    .filter(template -> template.getTriggerDescription().contains(keyword))
                    .collect(Collectors.toList());
        }

        @Override
        public SopTemplateEntity findLatestVersion(String category, String name) {
            return store.values().stream()
                    .filter(template -> Objects.equals(category, template.getCategory()))
                    .filter(template -> Objects.equals(name, template.getName()))
                    .max((left, right) -> {
                        Integer l = left.getVersion();
                        Integer r = right.getVersion();
                        if (l == null && r == null) {
                            return 0;
                        }
                        if (l == null) {
                            return -1;
                        }
                        if (r == null) {
                            return 1;
                        }
                        return Integer.compare(l, r);
                    })
                    .orElse(null);
        }
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
        public List<AgentPlanEntity> findBySopTemplateId(Long sopTemplateId) {
            return store.values().stream()
                    .filter(plan -> Objects.equals(sopTemplateId, plan.getSopTemplateId()))
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
            return store.values().stream()
                    .filter(task -> task.getStatus() == TaskStatusEnum.READY)
                    .collect(Collectors.toList());
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
            return Collections.emptyList();
        }
    }
}
