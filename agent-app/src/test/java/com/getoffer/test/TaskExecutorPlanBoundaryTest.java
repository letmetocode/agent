package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.trigger.job.TaskExecutor;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TaskExecutorPlanBoundaryTest {

    private final List<ThreadPoolExecutor> executors = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (ThreadPoolExecutor executor : executors) {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
        executors.clear();
    }

    @Test
    public void shouldReleaseClaimedTaskToReadyWhenPlanPaused() throws InterruptedException {
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        AgentTaskEntity claimedTask = buildClaimedTask(1L, 10L, 0, "owner-1");
        taskRepository.setClaimedTasks(Collections.singletonList(claimedTask));

        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        planRepository.setPlan(buildPlan(10L, PlanStatusEnum.PAUSED));

        TaskExecutor taskExecutor = newTaskExecutor(taskRepository, planRepository);
        taskExecutor.executeReadyTasks();

        boolean done = taskRepository.awaitUpdate(2, TimeUnit.SECONDS);
        Assertions.assertTrue(done, "task should be released for non-executable plan");
        Assertions.assertEquals(TaskStatusEnum.READY, taskRepository.getLastUpdatedStatus());
    }

    @Test
    public void shouldReleaseClaimedTaskToRefiningWhenPlanCancelledAndRetrying() throws InterruptedException {
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        AgentTaskEntity claimedTask = buildClaimedTask(2L, 20L, 2, "owner-2");
        taskRepository.setClaimedTasks(Collections.singletonList(claimedTask));

        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        planRepository.setPlan(buildPlan(20L, PlanStatusEnum.CANCELLED));

        TaskExecutor taskExecutor = newTaskExecutor(taskRepository, planRepository);
        taskExecutor.executeReadyTasks();

        boolean done = taskRepository.awaitUpdate(2, TimeUnit.SECONDS);
        Assertions.assertTrue(done, "retrying task should be released for non-executable plan");
        Assertions.assertEquals(TaskStatusEnum.REFINING, taskRepository.getLastUpdatedStatus());
    }

    @Test
    public void shouldRetryOnceAndCompleteAfterTimeout() throws InterruptedException {
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        AgentTaskEntity claimedTask = buildClaimedTask(3L, 30L, 0, "owner-3");
        taskRepository.setClaimedTasks(Collections.singletonList(claimedTask));

        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        planRepository.setPlan(buildPlan(30L, PlanStatusEnum.RUNNING));

        InMemoryTaskExecutionRepository executionRepository = new InMemoryTaskExecutionRepository();
        IAgentFactory agentFactory = new ScriptedAgentFactory(scriptedChatClient(List.of(
                ChatAction.timeout(200),
                ChatAction.success()
        )));

        TaskExecutor taskExecutor = newTaskExecutor(
                taskRepository,
                planRepository,
                executionRepository,
                agentFactory,
                50,
                1
        );

        taskExecutor.executeReadyTasks();

        boolean done = taskRepository.awaitUpdate(3, TimeUnit.SECONDS);
        Assertions.assertTrue(done, "task should finish after one timeout retry");
        Assertions.assertEquals(TaskStatusEnum.COMPLETED, taskRepository.getLastUpdatedStatus());
        Assertions.assertNotNull(taskRepository.getLastUpdatedTask());
        Assertions.assertEquals(1, taskRepository.getLastUpdatedTask().getCurrentRetry());

        List<TaskExecutionEntity> executions = executionRepository.findByTaskId(3L);
        Assertions.assertEquals(2, executions.size());
        Assertions.assertEquals("timeout", executions.get(0).getErrorType());
        Assertions.assertNull(executions.get(1).getErrorType());
    }

    @Test
    public void shouldFailAfterTimeoutRetryExhausted() throws InterruptedException {
        InMemoryAgentTaskRepository taskRepository = new InMemoryAgentTaskRepository();
        AgentTaskEntity claimedTask = buildClaimedTask(4L, 40L, 0, "owner-4");
        taskRepository.setClaimedTasks(Collections.singletonList(claimedTask));

        InMemoryAgentPlanRepository planRepository = new InMemoryAgentPlanRepository();
        planRepository.setPlan(buildPlan(40L, PlanStatusEnum.RUNNING));

        InMemoryTaskExecutionRepository executionRepository = new InMemoryTaskExecutionRepository();
        IAgentFactory agentFactory = new ScriptedAgentFactory(scriptedChatClient(List.of(
                ChatAction.timeout(200),
                ChatAction.timeout(200)
        )));

        TaskExecutor taskExecutor = newTaskExecutor(
                taskRepository,
                planRepository,
                executionRepository,
                agentFactory,
                50,
                1
        );

        taskExecutor.executeReadyTasks();

        boolean done = taskRepository.awaitUpdate(3, TimeUnit.SECONDS);
        Assertions.assertTrue(done, "task should fail after timeout retries exhausted");
        Assertions.assertEquals(TaskStatusEnum.FAILED, taskRepository.getLastUpdatedStatus());
        Assertions.assertNotNull(taskRepository.getLastUpdatedTask());
        Assertions.assertEquals(1, taskRepository.getLastUpdatedTask().getCurrentRetry());
        Assertions.assertTrue(taskRepository.getLastUpdatedTask().getOutputResult().contains("timed out"));

        List<TaskExecutionEntity> executions = executionRepository.findByTaskId(4L);
        Assertions.assertEquals(2, executions.size());
        Assertions.assertEquals("timeout", executions.get(0).getErrorType());
        Assertions.assertEquals("timeout", executions.get(1).getErrorType());
    }

    private TaskExecutor newTaskExecutor(InMemoryAgentTaskRepository taskRepository,
                                         InMemoryAgentPlanRepository planRepository) {
        return newTaskExecutor(
                taskRepository,
                planRepository,
                new InMemoryTaskExecutionRepository(),
                new ScriptedAgentFactory(scriptedChatClient(List.of(ChatAction.success()))),
                120000,
                1
        );
    }

    private TaskExecutor newTaskExecutor(InMemoryAgentTaskRepository taskRepository,
                                         InMemoryAgentPlanRepository planRepository,
                                         ITaskExecutionRepository executionRepository,
                                         IAgentFactory agentFactory,
                                         int executionTimeoutMs,
                                         int executionTimeoutRetryMax) {
        ThreadPoolExecutor worker = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(8));
        executors.add(worker);

        IPlanTaskEventRepository eventRepository = new InMemoryPlanTaskEventRepository();
        PlanTaskEventPublisher publisher = new PlanTaskEventPublisher(eventRepository);
        IAgentRegistryRepository agentRegistryRepository = new NoopAgentRegistryRepository();
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider =
                new DefaultListableBeanFactory().getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class);

        return new TaskExecutor(
                taskRepository,
                planRepository,
                publisher,
                executionRepository,
                agentFactory,
                agentRegistryRepository,
                new TaskDispatchDomainService(),
                new TaskExecutionDomainService(),
                new ObjectMapper(),
                worker,
                meterProvider,
                "test-instance",
                1,
                1,
                true,
                0.3D,
                1,
                120,
                30,
                executionTimeoutMs,
                executionTimeoutRetryMax,
                "worker,assistant",
                "critic,assistant",
                30000L,
                false,
                false
        );
    }

    private AgentPlanEntity buildPlan(Long planId, PlanStatusEnum status) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(planId);
        plan.setSessionId(1000L + planId);
        plan.setRouteDecisionId(1L);
        plan.setPlanGoal("plan-" + planId);
        plan.setExecutionGraph(Collections.singletonMap("nodes", Collections.emptyList()));
        plan.setDefinitionSnapshot(Collections.singletonMap("routeType", "HIT_PRODUCTION"));
        plan.setGlobalContext(Collections.emptyMap());
        plan.setStatus(status);
        plan.setPriority(0);
        plan.setVersion(0);
        return plan;
    }

    private AgentTaskEntity buildClaimedTask(Long taskId, Long planId, Integer retry, String owner) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(taskId);
        task.setPlanId(planId);
        task.setNodeId("node-" + taskId);
        task.setName("task-" + taskId);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setStatus(TaskStatusEnum.RUNNING);
        task.setDependencyNodeIds(Collections.emptyList());
        task.setInputContext(Collections.emptyMap());
        task.setConfigSnapshot(Collections.emptyMap());
        task.setCurrentRetry(retry);
        task.setMaxRetries(3);
        task.setClaimOwner(owner);
        task.setExecutionAttempt(1);
        task.setVersion(0);
        return task;
    }

    private ChatClient scriptedChatClient(List<ChatAction> actions) {
        Queue<ChatAction> queue = new ConcurrentLinkedQueue<>(actions == null ? Collections.emptyList() : actions);
        InvocationHandler requestHandler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(this, args);
                }
                if ("call".equals(method.getName())) {
                    ChatAction action = queue.poll();
                    if (action == null) {
                        action = ChatAction.success();
                    }
                    if (action.delayMs > 0) {
                        try {
                            Thread.sleep(action.delayMs);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("call interrupted", ex);
                        }
                    }
                    if (action.error != null) {
                        throw action.error;
                    }
                    ChatAction completedAction = action;
                    return Proxy.newProxyInstance(
                            ChatClient.CallResponseSpec.class.getClassLoader(),
                            new Class[]{ChatClient.CallResponseSpec.class},
                            (callProxy, callMethod, callArgs) -> {
                                if (callMethod.getDeclaringClass() == Object.class) {
                                    return callMethod.invoke(this, callArgs);
                                }
                                if ("chatResponse".equals(callMethod.getName())) {
                                    return completedAction.chatResponse;
                                }
                                if ("content".equals(callMethod.getName())) {
                                    return "";
                                }
                                return defaultValue(callMethod.getReturnType());
                            }
                    );
                }
                if (ChatClient.ChatClientRequestSpec.class.isAssignableFrom(method.getReturnType())) {
                    return proxy;
                }
                return defaultValue(method.getReturnType());
            }
        };

        ChatClient.ChatClientRequestSpec requestSpec = (ChatClient.ChatClientRequestSpec) Proxy.newProxyInstance(
                ChatClient.ChatClientRequestSpec.class.getClassLoader(),
                new Class[]{ChatClient.ChatClientRequestSpec.class},
                requestHandler
        );

        return (ChatClient) Proxy.newProxyInstance(
                ChatClient.class.getClassLoader(),
                new Class[]{ChatClient.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    if ("prompt".equals(method.getName())) {
                        return requestSpec;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == null || !returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }

    private static final class ChatAction {
        private final long delayMs;
        private final RuntimeException error;
        private final ChatResponse chatResponse;

        private ChatAction(long delayMs, RuntimeException error, ChatResponse chatResponse) {
            this.delayMs = delayMs;
            this.error = error;
            this.chatResponse = chatResponse;
        }

        private static ChatAction timeout(long delayMs) {
            return new ChatAction(Math.max(delayMs, 0L), null, null);
        }

        private static ChatAction success() {
            return new ChatAction(0L, null, null);
        }
    }

    private static final class InMemoryAgentTaskRepository implements IAgentTaskRepository {
        private List<AgentTaskEntity> claimedTasks = Collections.emptyList();
        private final CountDownLatch updateLatch = new CountDownLatch(1);
        private volatile TaskStatusEnum lastUpdatedStatus;
        private volatile AgentTaskEntity lastUpdatedTask;

        public void setClaimedTasks(List<AgentTaskEntity> claimedTasks) {
            this.claimedTasks = claimedTasks == null ? Collections.emptyList() : claimedTasks;
        }

        public boolean awaitUpdate(long timeout, TimeUnit unit) throws InterruptedException {
            return updateLatch.await(timeout, unit);
        }

        public TaskStatusEnum getLastUpdatedStatus() {
            return lastUpdatedStatus;
        }

        public AgentTaskEntity getLastUpdatedTask() {
            return lastUpdatedTask;
        }

        @Override
        public List<AgentTaskEntity> claimReadyLikeTasks(String claimOwner, int limit, int leaseSeconds) {
            return claimedTasks;
        }

        @Override
        public List<AgentTaskEntity> claimRefiningTasks(String claimOwner, int limit, int leaseSeconds) {
            return Collections.emptyList();
        }

        @Override
        public boolean updateClaimedTaskState(AgentTaskEntity entity) {
            this.lastUpdatedStatus = entity == null ? null : entity.getStatus();
            this.lastUpdatedTask = entity;
            updateLatch.countDown();
            return true;
        }

        @Override
        public boolean renewClaimLease(Long taskId, String claimOwner, Integer executionAttempt, int leaseSeconds) {
            return true;
        }

        @Override
        public long countExpiredRunningTasks() {
            return 0;
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
            return Collections.emptyList();
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

    private static final class InMemoryTaskExecutionRepository implements ITaskExecutionRepository {
        private final AtomicLong sequence = new AtomicLong(1L);
        private final Map<Long, List<TaskExecutionEntity>> executionsByTask = new HashMap<>();

        @Override
        public TaskExecutionEntity save(TaskExecutionEntity entity) {
            TaskExecutionEntity stored = copy(entity);
            stored.setId(sequence.getAndIncrement());
            executionsByTask.computeIfAbsent(stored.getTaskId(), key -> new ArrayList<>()).add(stored);
            return stored;
        }

        @Override
        public boolean deleteById(Long id) {
            if (id == null) {
                return false;
            }
            for (List<TaskExecutionEntity> list : executionsByTask.values()) {
                if (list.removeIf(item -> id.equals(item.getId()))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public TaskExecutionEntity findById(Long id) {
            if (id == null) {
                return null;
            }
            for (List<TaskExecutionEntity> list : executionsByTask.values()) {
                for (TaskExecutionEntity item : list) {
                    if (id.equals(item.getId())) {
                        return copy(item);
                    }
                }
            }
            return null;
        }

        @Override
        public List<TaskExecutionEntity> findByTaskId(Long taskId) {
            List<TaskExecutionEntity> stored = executionsByTask.getOrDefault(taskId, Collections.emptyList());
            List<TaskExecutionEntity> result = new ArrayList<>(stored.size());
            for (TaskExecutionEntity item : stored) {
                result.add(copy(item));
            }
            result.sort(Comparator.comparing(TaskExecutionEntity::getAttemptNumber));
            return result;
        }

        @Override
        public List<TaskExecutionEntity> findByTaskIdOrderByAttempt(Long taskId) {
            List<TaskExecutionEntity> result = findByTaskId(taskId);
            result.sort(Comparator.comparing(TaskExecutionEntity::getAttemptNumber).reversed());
            return result;
        }

        @Override
        public TaskExecutionEntity findByTaskIdAndAttempt(Long taskId, Integer attemptNumber) {
            if (taskId == null || attemptNumber == null) {
                return null;
            }
            for (TaskExecutionEntity item : executionsByTask.getOrDefault(taskId, Collections.emptyList())) {
                if (attemptNumber.equals(item.getAttemptNumber())) {
                    return copy(item);
                }
            }
            return null;
        }

        @Override
        public List<TaskExecutionEntity> findAll() {
            List<TaskExecutionEntity> all = new ArrayList<>();
            for (List<TaskExecutionEntity> values : executionsByTask.values()) {
                for (TaskExecutionEntity item : values) {
                    all.add(copy(item));
                }
            }
            return all;
        }

        @Override
        public Integer getMaxAttemptNumber(Long taskId) {
            int max = 0;
            for (TaskExecutionEntity item : executionsByTask.getOrDefault(taskId, Collections.emptyList())) {
                if (item.getAttemptNumber() != null && item.getAttemptNumber() > max) {
                    max = item.getAttemptNumber();
                }
            }
            return max;
        }

        @Override
        public Map<Long, Long> findLatestExecutionTimeByTaskIds(List<Long> taskIds) {
            Map<Long, Long> result = new HashMap<>();
            if (taskIds == null || taskIds.isEmpty()) {
                return result;
            }
            for (Long taskId : taskIds) {
                if (taskId == null) {
                    continue;
                }
                TaskExecutionEntity latest = findByTaskIdOrderByAttempt(taskId).stream().findFirst().orElse(null);
                if (latest != null) {
                    result.put(taskId, latest.getExecutionTimeMs());
                }
            }
            return result;
        }

        @Override
        public List<TaskExecutionEntity> batchSave(List<TaskExecutionEntity> entities) {
            List<TaskExecutionEntity> result = new ArrayList<>();
            if (entities == null) {
                return result;
            }
            for (TaskExecutionEntity entity : entities) {
                result.add(save(entity));
            }
            return result;
        }

        private TaskExecutionEntity copy(TaskExecutionEntity source) {
            TaskExecutionEntity target = new TaskExecutionEntity();
            target.setId(source.getId());
            target.setTaskId(source.getTaskId());
            target.setAttemptNumber(source.getAttemptNumber());
            target.setPromptSnapshot(source.getPromptSnapshot());
            target.setLlmResponseRaw(source.getLlmResponseRaw());
            target.setModelName(source.getModelName());
            target.setTokenUsage(source.getTokenUsage());
            target.setExecutionTimeMs(source.getExecutionTimeMs());
            target.setIsValid(source.getIsValid());
            target.setValidationFeedback(source.getValidationFeedback());
            target.setErrorMessage(source.getErrorMessage());
            target.setErrorType(source.getErrorType());
            target.setCreatedAt(source.getCreatedAt());
            return target;
        }
    }

    private static final class InMemoryAgentPlanRepository implements IAgentPlanRepository {
        private AgentPlanEntity plan;

        public void setPlan(AgentPlanEntity plan) {
            this.plan = plan;
        }

        @Override
        public AgentPlanEntity findById(Long id) {
            return plan;
        }

        @Override
        public AgentPlanEntity save(AgentPlanEntity entity) {
            return entity;
        }

        @Override
        public AgentPlanEntity update(AgentPlanEntity entity) {
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
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
            return Collections.emptyList();
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

    private static final class ScriptedAgentFactory implements IAgentFactory {
        private final ChatClient chatClient;

        private ScriptedAgentFactory(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public ChatClient createAgent(String agentKey, String conversationId) {
            return chatClient;
        }

        @Override
        public ChatClient createAgent(Long agentId, String conversationId) {
            return chatClient;
        }

        @Override
        public ChatClient createAgent(AgentRegistryEntity agent, String conversationId) {
            return chatClient;
        }

        @Override
        public ChatClient createAgent(String agentKey, String conversationId, String systemPromptSuffix) {
            return chatClient;
        }

        @Override
        public ChatClient createAgent(Long agentId, String conversationId, String systemPromptSuffix) {
            return chatClient;
        }

        @Override
        public ChatClient createAgent(AgentRegistryEntity agent, String conversationId, String systemPromptSuffix) {
            return chatClient;
        }
    }

    private static final class NoopAgentRegistryRepository implements IAgentRegistryRepository {

        @Override
        public AgentRegistryEntity save(AgentRegistryEntity entity) {
            return entity;
        }

        @Override
        public AgentRegistryEntity update(AgentRegistryEntity entity) {
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public AgentRegistryEntity findById(Long id) {
            return null;
        }

        @Override
        public AgentRegistryEntity findByKey(String key) {
            return null;
        }

        @Override
        public List<AgentRegistryEntity> findAll() {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRegistryEntity> findByActive(Boolean isActive) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
            return Collections.emptyList();
        }

        @Override
        public boolean existsByKey(String key) {
            return false;
        }
    }
}
