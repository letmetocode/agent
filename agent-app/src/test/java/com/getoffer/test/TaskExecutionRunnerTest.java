package com.getoffer.test;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.trigger.job.TaskExecutionRunner;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;

public class TaskExecutionRunnerTest {

    private final TaskExecutionRunner runner = new TaskExecutionRunner();

    @Test
    public void shouldSkipWhenClaimIsInvalid() {
        AgentTaskEntity task = buildRunningTask(1L, 10L);
        task.setClaimOwner(null);
        task.setExecutionAttempt(null);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.validClaim = false;

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("skip_invalid_claim", result.outcome());
        Assertions.assertEquals("none", result.errorType());
        Assertions.assertEquals(0, support.callTaskClientCount);
        Assertions.assertTrue(support.stopHeartbeatCalled);
    }

    @Test
    public void shouldCompleteWorkerTaskWithoutValidation() {
        AgentTaskEntity task = buildRunningTask(2L, 20L);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.plan = buildRunningPlan(20L);
        support.validationRequired = false;
        support.extractedContent = "worker-output";

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("completed", result.outcome());
        Assertions.assertEquals("none", result.errorType());
        Assertions.assertEquals(TaskStatusEnum.COMPLETED, task.getStatus());
        Assertions.assertEquals("worker-output", task.getOutputResult());
        Assertions.assertTrue(support.safeSaveExecutionCalled);
        Assertions.assertTrue(support.safeUpdateClaimedTaskCalled);
        Assertions.assertTrue(support.syncBlackboardCalled);
        Assertions.assertEquals(List.of(PlanTaskEventTypeEnum.TASK_COMPLETED, PlanTaskEventTypeEnum.TASK_LOG), support.publishedEvents);
    }

    @Test
    public void shouldFailWhenTimeoutRetryExhausted() {
        AgentTaskEntity task = buildRunningTask(3L, 30L);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.plan = buildRunningPlan(30L);
        support.timeoutRetryEnabled = false;
        support.callSequence.add(new TaskExecutionRunner.TaskCallTimeoutException("Task execution timed out", new RuntimeException("timeout")));

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("failed", result.outcome());
        Assertions.assertEquals("timeout", result.errorType());
        Assertions.assertEquals(TaskStatusEnum.FAILED, task.getStatus());
        Assertions.assertTrue(task.getOutputResult().contains("timed out"));
        Assertions.assertTrue(support.persistTimeoutExecutionCalled);
        Assertions.assertEquals(List.of(false), support.timeoutRetryMetricMarks);
        Assertions.assertEquals(0, support.applyTimeoutRetryCount);
        Assertions.assertEquals(List.of(PlanTaskEventTypeEnum.TASK_COMPLETED, PlanTaskEventTypeEnum.TASK_LOG), support.publishedEvents);
    }

    @Test
    public void shouldRetryAfterTimeoutAndThenComplete() {
        AgentTaskEntity task = buildRunningTask(4L, 40L);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.plan = buildRunningPlan(40L);
        support.validationRequired = false;
        support.extractedContent = "retry-success";
        support.timeoutRetryDecisions.add(true);
        support.callSequence.add(new TaskExecutionRunner.TaskCallTimeoutException("Task execution timed out", new RuntimeException("timeout")));

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("completed", result.outcome());
        Assertions.assertEquals("timeout", result.errorType());
        Assertions.assertEquals(TaskStatusEnum.COMPLETED, task.getStatus());
        Assertions.assertEquals(2, support.callTaskClientCount);
        Assertions.assertEquals(1, support.applyTimeoutRetryCount);
        Assertions.assertEquals(List.of(true), support.timeoutRetryMetricMarks);
        Assertions.assertEquals(List.of(PlanTaskEventTypeEnum.TASK_COMPLETED, PlanTaskEventTypeEnum.TASK_LOG), support.publishedEvents);
    }

    @Test
    public void shouldMarkValidationRejectedWhenValidationFails() {
        AgentTaskEntity task = buildRunningTask(5L, 50L);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.plan = buildRunningPlan(50L);
        support.validationRequired = true;
        support.validationResult = new TaskExecutionRunner.ValidationResult(false, "bad quality");
        support.extractedContent = "draft-output";

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("validation_rejected", result.outcome());
        Assertions.assertEquals("none", result.errorType());
        Assertions.assertEquals(TaskStatusEnum.REFINING, task.getStatus());
        Assertions.assertEquals(1, support.handleValidationFailureCount);
        Assertions.assertFalse(support.syncBlackboardCalled);
        Assertions.assertTrue(support.publishedEvents.isEmpty());
    }

    @Test
    public void shouldRollbackTargetWhenCriticRejects() {
        AgentTaskEntity task = buildRunningTask(6L, 60L);
        task.setTaskType(TaskTypeEnum.CRITIC);
        FakeExecutionSupport support = new FakeExecutionSupport();
        support.plan = buildRunningPlan(60L);
        support.criticTask = true;
        support.criticDecision = new TaskExecutionRunner.CriticDecision(false, "need rewrite");
        support.extractedContent = "critic-response";

        TaskExecutionRunner.ExecutionResult result = runner.run(task, support);

        Assertions.assertEquals("critic_rejected", result.outcome());
        Assertions.assertEquals(TaskStatusEnum.PENDING, task.getStatus());
        Assertions.assertEquals("critic-response", task.getOutputResult());
        Assertions.assertEquals(1, support.rollbackTargetCount);
        Assertions.assertEquals(List.of(PlanTaskEventTypeEnum.TASK_LOG), support.publishedEvents);
    }

    private AgentTaskEntity buildRunningTask(Long taskId, Long planId) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(taskId);
        task.setPlanId(planId);
        task.setNodeId("node-" + taskId);
        task.setName("task-" + taskId);
        task.setTaskType(TaskTypeEnum.WORKER);
        task.setStatus(TaskStatusEnum.RUNNING);
        task.setInputContext(new HashMap<>());
        task.setConfigSnapshot(new HashMap<>());
        task.setCurrentRetry(0);
        task.setMaxRetries(3);
        task.setClaimOwner("owner");
        task.setExecutionAttempt(1);
        task.setVersion(0);
        return task;
    }

    private AgentPlanEntity buildRunningPlan(Long planId) {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(planId);
        plan.setStatus(PlanStatusEnum.RUNNING);
        return plan;
    }

    private static final class FakeExecutionSupport implements TaskExecutionRunner.ExecutionSupport {
        private boolean validClaim = true;
        private boolean stopHeartbeatCalled = false;
        private AgentPlanEntity plan = null;
        private boolean validationRequired = false;
        private TaskExecutionRunner.ValidationResult validationResult = new TaskExecutionRunner.ValidationResult(true, "ok");
        private TaskExecutionRunner.CriticDecision criticDecision = new TaskExecutionRunner.CriticDecision(true, "ok");
        private boolean timeoutRetryEnabled = false;
        private boolean criticTask = false;
        private int applyTimeoutRetryCount = 0;
        private int callTaskClientCount = 0;
        private int rollbackTargetCount = 0;
        private int handleValidationFailureCount = 0;
        private String extractedContent = "";
        private boolean persistTimeoutExecutionCalled = false;
        private boolean safeSaveExecutionCalled = false;
        private boolean safeUpdateClaimedTaskCalled = false;
        private boolean syncBlackboardCalled = false;
        private final Queue<Object> callSequence = new ArrayDeque<>();
        private final Queue<Boolean> timeoutRetryDecisions = new ArrayDeque<>();
        private final List<Boolean> timeoutRetryMetricMarks = new ArrayList<>();
        private final List<PlanTaskEventTypeEnum> publishedEvents = new ArrayList<>();

        @Override
        public boolean hasValidClaim(AgentTaskEntity task) {
            return validClaim;
        }

        @Override
        public ScheduledFuture<?> startHeartbeat(AgentTaskEntity task) {
            return null;
        }

        @Override
        public void stopHeartbeat(ScheduledFuture<?> future) {
            stopHeartbeatCalled = true;
        }

        @Override
        public AgentPlanEntity findPlan(Long planId) {
            return plan;
        }

        @Override
        public boolean releaseClaimForNonExecutablePlan(AgentTaskEntity task, AgentPlanEntity plan) {
            return true;
        }

        @Override
        public void recordRetryDistribution(AgentTaskEntity task) {
        }

        @Override
        public int resolveAttemptNumber(AgentTaskEntity task) {
            return 1;
        }

        @Override
        public boolean safeUpdateClaimedTask(AgentTaskEntity task) {
            safeUpdateClaimedTaskCalled = true;
            return true;
        }

        @Override
        public boolean isCriticTask(AgentTaskEntity task) {
            return criticTask;
        }

        @Override
        public String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
            return "critic-prompt";
        }

        @Override
        public String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan) {
            return "refine-prompt";
        }

        @Override
        public String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
            return "worker-prompt";
        }

        @Override
        public String buildRetrySystemPrompt(AgentTaskEntity task) {
            return "";
        }

        @Override
        public ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix) {
            return null;
        }

        @Override
        public ChatResponse callTaskClientWithTimeout(ChatClient taskClient, String prompt) {
            callTaskClientCount++;
            Object next = callSequence.poll();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (next instanceof ChatResponse chatResponse) {
                return chatResponse;
            }
            return null;
        }

        @Override
        public void persistTimeoutExecution(TaskExecutionEntity execution, long startTime, TaskExecutionRunner.TaskCallTimeoutException timeoutException) {
            persistTimeoutExecutionCalled = true;
        }

        @Override
        public boolean canTimeoutRetry(AgentTaskEntity task, int timeoutRetryCount) {
            if (!timeoutRetryDecisions.isEmpty()) {
                return Boolean.TRUE.equals(timeoutRetryDecisions.poll());
            }
            return timeoutRetryEnabled;
        }

        @Override
        public void recordTimeoutMetrics(AgentTaskEntity task, boolean retrying) {
            timeoutRetryMetricMarks.add(retrying);
        }

        @Override
        public void applyTimeoutRetry(AgentTaskEntity task, String errorMessage) {
            applyTimeoutRetryCount++;
        }

        @Override
        public void safeSaveExecution(TaskExecutionEntity execution) {
            safeSaveExecutionCalled = true;
        }

        @Override
        public Map<String, Object> buildTaskData(AgentTaskEntity task) {
            return new HashMap<>();
        }

        @Override
        public Map<String, Object> buildTaskLog(AgentTaskEntity task) {
            return new HashMap<>();
        }

        @Override
        public void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data) {
            publishedEvents.add(eventType);
        }

        @Override
        public TaskExecutionRunner.CriticDecision parseCriticDecision(String response) {
            return criticDecision;
        }

        @Override
        public void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback) {
            rollbackTargetCount++;
        }

        @Override
        public boolean needsValidation(AgentTaskEntity task) {
            return validationRequired;
        }

        @Override
        public TaskExecutionRunner.ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
            return validationResult;
        }

        @Override
        public void handleValidationFailure(AgentTaskEntity task, String feedback) {
            handleValidationFailureCount++;
            task.startRefining();
        }

        @Override
        public void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output) {
            syncBlackboardCalled = true;
        }

        @Override
        public String classifyError(Throwable throwable) {
            return "runtime_error";
        }

        @Override
        public String extractContent(ChatResponse chatResponse) {
            return extractedContent;
        }

        @Override
        public String extractModelName(ChatResponse chatResponse) {
            return "mock-model";
        }

        @Override
        public Map<String, Object> extractTokenUsage(ChatResponse chatResponse) {
            return null;
        }
    }
}
