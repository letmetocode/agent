package com.getoffer.trigger.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task executor: run READY tasks, write results, and sync blackboard.
 */
@Slf4j
@Component
public class TaskExecutor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private static final String METRIC_EXECUTION_TOTAL = "agent.task.execution.total";
    private static final String METRIC_EXECUTION_DURATION = "agent.task.execution.duration";
    private static final String METRIC_EXECUTION_FAILURE_TOTAL = "agent.task.execution.failure.total";
    private static final int PLAN_CONTEXT_UPDATE_MAX_RETRY = 3;

    private final IAgentTaskRepository agentTaskRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IAgentFactory agentFactory;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final TaskAgentSelectionDomainService taskAgentSelectionDomainService;
    private final TaskDispatchDomainService taskDispatchDomainService;
    private final TaskExecutionDomainService taskExecutionDomainService;
    private final TaskPromptDomainService taskPromptDomainService;
    private final TaskEvaluationDomainService taskEvaluationDomainService;
    private final TaskRecoveryDomainService taskRecoveryDomainService;
    private final ObjectMapper objectMapper;
    private final String claimOwner;
    private final int claimBatchSize;
    private final int claimLeaseSeconds;
    private final int claimHeartbeatSeconds;
    private final int claimMaxPerTick;
    private final boolean claimReadyFirst;
    private final double refiningMaxRatio;
    private final int refiningMinPerTick;
    private final int executionTimeoutMs;
    private final int executionTimeoutRetryMax;
    private final ThreadPoolExecutor taskExecutionWorker;
    private final ExecutorService taskCallExecutor;
    private final Semaphore dispatchPermits;
    private final AtomicInteger inFlightTasks;
    private final ScheduledExecutorService heartbeatScheduler;
    private final AtomicLong monitorTick;
    private final AtomicLong expiredRunningGauge;
    private final MeterRegistry meterRegistry;
    private final Counter claimPollCounter;
    private final Counter claimSuccessCounter;
    private final Counter claimEmptyCounter;
    private final Counter claimNullOnlyCounter;
    private final Counter claimReclaimedCounter;
    private final Counter claimReadyCounter;
    private final Counter claimRefiningCounter;
    private final Counter claimReadyFallbackCounter;
    private final Counter claimRefiningFallbackCounter;
    private final DistributionSummary claimAttemptSummary;
    private final Counter heartbeatSuccessCounter;
    private final Counter heartbeatGuardRejectCounter;
    private final Counter heartbeatErrorCounter;
    private final Counter claimedUpdateSuccessCounter;
    private final Counter claimedUpdateGuardRejectCounter;
    private final Counter claimedUpdateErrorCounter;
    private final Counter dispatchSuccessCounter;
    private final Counter dispatchRejectCounter;
    private final DistributionSummary claimToStartLatencySummary;
    private final DistributionSummary executionRetrySummary;
    private final Counter expiredRunningDetectedCounter;
    private final Counter expiredRunningCheckErrorCounter;
    private final List<String> workerFallbackAgentKeys;
    private final List<String> criticFallbackAgentKeys;
    private final long defaultAgentCacheTtlMs;
    private volatile AgentRegistryEntity cachedDefaultAgent;
    private volatile long cachedDefaultAgentAtMillis;
    private final boolean auditLogEnabled;
    private final boolean auditSuccessLogEnabled;

    public TaskExecutor(IAgentTaskRepository agentTaskRepository,
                        IAgentPlanRepository agentPlanRepository,
                        PlanTaskEventPublisher planTaskEventPublisher,
                        ITaskExecutionRepository taskExecutionRepository,
                        IAgentFactory agentFactory,
                        IAgentRegistryRepository agentRegistryRepository,
                        TaskAgentSelectionDomainService taskAgentSelectionDomainService,
                        TaskDispatchDomainService taskDispatchDomainService,
                        TaskExecutionDomainService taskExecutionDomainService,
                        TaskPromptDomainService taskPromptDomainService,
                        TaskEvaluationDomainService taskEvaluationDomainService,
                        TaskRecoveryDomainService taskRecoveryDomainService,
                        ObjectMapper objectMapper,
                        @Qualifier("taskExecutionWorker") ThreadPoolExecutor taskExecutionWorker,
                        ObjectProvider<MeterRegistry> meterRegistryProvider,
                        @Value("${executor.instance-id:}") String configuredInstanceId,
                        @Value("${executor.claim.batch-size:100}") int claimBatchSize,
                        @Value("${executor.claim.max-per-tick:100}") int claimMaxPerTick,
                        @Value("${executor.claim.ready-first:true}") boolean claimReadyFirst,
                        @Value("${executor.claim.refining-max-ratio:0.3}") double refiningMaxRatio,
                        @Value("${executor.claim.refining-min-per-tick:1}") int refiningMinPerTick,
                        @Value("${executor.claim.lease-seconds:120}") int claimLeaseSeconds,
                        @Value("${executor.claim.heartbeat-seconds:30}") int claimHeartbeatSeconds,
                        @Value("${executor.execution.timeout-ms:120000}") int executionTimeoutMs,
                        @Value("${executor.execution.timeout-retry-max:1}") int executionTimeoutRetryMax,
                        @Value("${executor.agent.fallback-worker-keys:worker,assistant,java_coder,default}") String workerFallbackAgentKeys,
                        @Value("${executor.agent.fallback-critic-keys:critic,assistant,java_coder,default}") String criticFallbackAgentKeys,
                        @Value("${executor.agent.default-cache-ttl-ms:30000}") long defaultAgentCacheTtlMs,
                        @Value("${executor.observability.audit-log-enabled:true}") boolean auditLogEnabled,
                        @Value("${executor.observability.audit-success-log-enabled:false}") boolean auditSuccessLogEnabled) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.taskExecutionRepository = taskExecutionRepository;
        this.agentFactory = agentFactory;
        this.agentRegistryRepository = agentRegistryRepository;
        this.taskAgentSelectionDomainService = taskAgentSelectionDomainService;
        this.taskDispatchDomainService = taskDispatchDomainService;
        this.taskExecutionDomainService = taskExecutionDomainService;
        this.taskPromptDomainService = taskPromptDomainService;
        this.taskEvaluationDomainService = taskEvaluationDomainService;
        this.taskRecoveryDomainService = taskRecoveryDomainService;
        this.objectMapper = objectMapper;
        this.taskExecutionWorker = taskExecutionWorker;
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.claimOwner = resolveInstanceId(configuredInstanceId);
        this.claimBatchSize = claimBatchSize > 0 ? claimBatchSize : 100;
        this.claimMaxPerTick = claimMaxPerTick > 0 ? claimMaxPerTick : this.claimBatchSize;
        this.claimReadyFirst = claimReadyFirst;
        double normalizedRatio = Double.isNaN(refiningMaxRatio) || Double.isInfinite(refiningMaxRatio) ? 0.3D : refiningMaxRatio;
        this.refiningMaxRatio = Math.max(0D, Math.min(normalizedRatio, 1D));
        this.refiningMinPerTick = Math.max(refiningMinPerTick, 0);
        this.claimLeaseSeconds = claimLeaseSeconds > 0 ? claimLeaseSeconds : 120;
        this.claimHeartbeatSeconds = claimHeartbeatSeconds > 0 ? claimHeartbeatSeconds : 30;
        this.executionTimeoutMs = executionTimeoutMs > 0 ? executionTimeoutMs : 120000;
        this.executionTimeoutRetryMax = Math.max(executionTimeoutRetryMax, 0);
        int maxInflight = Math.max(taskExecutionWorker.getMaximumPoolSize(), 1);
        this.dispatchPermits = new Semaphore(maxInflight);
        this.inFlightTasks = new AtomicInteger(0);
        AtomicInteger taskCallThreadCounter = new AtomicInteger(0);
        this.taskCallExecutor = Executors.newFixedThreadPool(maxInflight, runnable -> {
            Thread thread = new Thread(runnable, "task-llm-call-" + taskCallThreadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "task-claim-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.monitorTick = new AtomicLong(0L);
        this.expiredRunningGauge = new AtomicLong(0L);
        this.claimPollCounter = counter("agent.task.claim.poll.total");
        this.claimSuccessCounter = counter("agent.task.claim.success.total");
        this.claimEmptyCounter = counter("agent.task.claim.empty.total");
        this.claimNullOnlyCounter = counter("agent.task.claim.null_only.total");
        this.claimReclaimedCounter = counter("agent.task.claim.reclaimed.total");
        this.claimReadyCounter = counter("agent.task.claim.ready.count");
        this.claimRefiningCounter = counter("agent.task.claim.refining.count");
        this.claimReadyFallbackCounter = counter("agent.task.claim.ready.fallback.count");
        this.claimRefiningFallbackCounter = counter("agent.task.claim.refining.fallback.count");
        this.claimAttemptSummary = DistributionSummary.builder("agent.task.claim.execution_attempt")
                .description("Claim 成功任务的 execution_attempt 分布")
                .baseUnit("attempt")
                .register(meterRegistry);
        this.heartbeatSuccessCounter = counter("agent.task.heartbeat.success.total");
        this.heartbeatGuardRejectCounter = counter("agent.task.heartbeat.guard_reject.total");
        this.heartbeatErrorCounter = counter("agent.task.heartbeat.error.total");
        this.claimedUpdateSuccessCounter = counter("agent.task.claimed_update.success.total");
        this.claimedUpdateGuardRejectCounter = counter("agent.task.claimed_update.guard_reject.total");
        this.claimedUpdateErrorCounter = counter("agent.task.claimed_update.error.total");
        this.dispatchSuccessCounter = counter("agent.task.dispatch.success.total");
        this.dispatchRejectCounter = counter("agent.task.dispatch.reject.total");
        this.claimToStartLatencySummary = DistributionSummary.builder("agent.task.claim_to_start.latency")
                .description("任务 claim 到执行开始的延迟分布")
                .baseUnit("ms")
                .register(meterRegistry);
        this.executionRetrySummary = DistributionSummary.builder("agent.task.execution.retry.count")
                .description("任务执行时的 current_retry 分布")
                .baseUnit("retry")
                .register(meterRegistry);
        this.expiredRunningDetectedCounter = counter("agent.task.expired_running.detected.total");
        this.expiredRunningCheckErrorCounter = counter("agent.task.expired_running.check_error.total");
        this.workerFallbackAgentKeys = taskAgentSelectionDomainService.parseFallbackAgentKeys(workerFallbackAgentKeys, "worker", "assistant");
        this.criticFallbackAgentKeys = taskAgentSelectionDomainService.parseFallbackAgentKeys(criticFallbackAgentKeys, "critic", "assistant");
        this.defaultAgentCacheTtlMs = defaultAgentCacheTtlMs > 0 ? defaultAgentCacheTtlMs : 30000L;
        this.auditLogEnabled = auditLogEnabled;
        this.auditSuccessLogEnabled = auditSuccessLogEnabled;
        Gauge.builder("agent.task.expired_running.current", expiredRunningGauge, AtomicLong::get)
                .description("当前过期 RUNNING 任务数量")
                .register(meterRegistry);
        Gauge.builder("agent.task.worker.inflight.current", inFlightTasks, AtomicInteger::get)
                .description("任务执行线程池当前 in-flight 数量")
                .register(meterRegistry);
        Gauge.builder("agent.task.worker.queue.current", taskExecutionWorker, executor -> executor.getQueue().size())
                .description("任务执行线程池当前排队任务数量")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${executor.poll-interval-ms:1000}", scheduler = "taskExecutorScheduler")
    public void executeReadyTasks() {
        int claimLimit = resolveClaimLimit();
        if (claimLimit <= 0) {
            emitExpiredRunningMetric();
            return;
        }

        int reservedSlots = reserveDispatchSlots(claimLimit);
        if (reservedSlots <= 0) {
            emitExpiredRunningMetric();
            return;
        }

        List<AgentTaskEntity> claimedTasks = claimTasks(reservedSlots);
        if (claimedTasks.isEmpty()) {
            releaseDispatchSlots(reservedSlots);
            emitExpiredRunningMetric();
            return;
        }

        int redundantSlots = reservedSlots - claimedTasks.size();
        if (redundantSlots > 0) {
            releaseDispatchSlots(redundantSlots);
        }
        for (AgentTaskEntity task : claimedTasks) {
            if (!dispatchClaimedTask(task)) {
                releaseDispatchSlots(1);
            }
        }

        emitExpiredRunningMetric();
    }

    private int resolveClaimLimit() {
        return taskDispatchDomainService.resolveClaimLimit(
                claimBatchSize,
                claimMaxPerTick,
                dispatchPermits.availablePermits()
        );
    }

    private int reserveDispatchSlots(int expected) {
        int limit = Math.max(expected, 0);
        int reserved = 0;
        for (int i = 0; i < limit; i++) {
            if (!dispatchPermits.tryAcquire()) {
                break;
            }
            reserved++;
        }
        return reserved;
    }

    private void releaseDispatchSlots(int count) {
        if (count > 0) {
            dispatchPermits.release(count);
        }
    }

    private List<AgentTaskEntity> claimTasks(int limit) {
        claimPollCounter.increment();
        TaskDispatchDomainService.ClaimPlan claimPlan = taskDispatchDomainService.planClaim(
                Math.max(limit, 0),
                claimReadyFirst,
                refiningMaxRatio,
                refiningMinPerTick
        );
        if (claimPlan.claimLimit() <= 0) {
            claimEmptyCounter.increment();
            return Collections.emptyList();
        }

        List<AgentTaskEntity> claimed = new ArrayList<>(claimPlan.claimLimit());
        for (TaskDispatchDomainService.ClaimSlot slot : claimPlan.primarySlots()) {
            claimTasksByType(claimed, slot.limit(), slot.readyLike(), slot.fallback());
        }

        int remaining = claimPlan.claimLimit() - claimed.size();
        if (remaining > 0) {
            for (Boolean readyLike : claimPlan.fallbackOrder()) {
                claimTasksByType(claimed, remaining, Boolean.TRUE.equals(readyLike), true);
                remaining = claimPlan.claimLimit() - claimed.size();
                if (remaining <= 0) {
                    break;
                }
            }
        }

        if (claimed.isEmpty() && claimPlan.claimLimit() > 0) {
            claimEmptyCounter.increment();
        }
        return claimed;
    }

    private void claimTasksByType(List<AgentTaskEntity> claimed,
                                  int limit,
                                  boolean readyLike,
                                  boolean fallback) {
        int normalizedLimit = Math.max(limit, 0);
        if (normalizedLimit <= 0) {
            return;
        }
        List<AgentTaskEntity> tasks = readyLike
                ? agentTaskRepository.claimReadyLikeTasks(claimOwner, normalizedLimit, claimLeaseSeconds)
                : agentTaskRepository.claimRefiningTasks(claimOwner, normalizedLimit, claimLeaseSeconds);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        int accepted = 0;
        for (AgentTaskEntity task : tasks) {
            if (task == null) {
                continue;
            }
            onTaskClaimed(task);
            claimed.add(task);
            accepted++;
            if (readyLike) {
                claimReadyCounter.increment();
            } else {
                claimRefiningCounter.increment();
            }
        }
        if (accepted == 0) {
            claimNullOnlyCounter.increment();
            log.warn("Claim result contains only null task records. owner={}, requestedLimit={}, type={}",
                    claimOwner, normalizedLimit, readyLike ? "ready_like" : "refining");
            return;
        }
        if (fallback) {
            if (readyLike) {
                claimReadyFallbackCounter.increment(accepted);
            } else {
                claimRefiningFallbackCounter.increment(accepted);
            }
        }
    }

    private void onTaskClaimed(AgentTaskEntity task) {
        claimSuccessCounter.increment();
        if (task.getExecutionAttempt() != null && task.getExecutionAttempt() > 0) {
            claimAttemptSummary.record(task.getExecutionAttempt());
        }
        if (Boolean.TRUE.equals(task.getLeaseReclaimed())) {
            claimReclaimedCounter.increment();
            emitTaskAudit("claim_reclaimed", task,
                    "lease_reclaimed=true");
        } else if (auditLogEnabled && auditSuccessLogEnabled) {
            emitTaskAudit("claim_acquired", task, "lease_reclaimed=false");
        }
    }

    private boolean dispatchClaimedTask(AgentTaskEntity task) {
        if (task == null) {
            return false;
        }
        try {
            taskExecutionWorker.execute(() -> executeClaimedTask(task));
            dispatchSuccessCounter.increment();
            if (auditLogEnabled && auditSuccessLogEnabled) {
                emitTaskAudit("dispatch_submitted", task, "-");
            }
            return true;
        } catch (RejectedExecutionException ex) {
            dispatchRejectCounter.increment();
            log.warn("Dispatch rejected by worker executor. taskId={}, owner={}, attempt={}, active={}, queueSize={}, error={}",
                    task.getId(), task.getClaimOwner(), task.getExecutionAttempt(),
                    taskExecutionWorker.getActiveCount(),
                    taskExecutionWorker.getQueue().size(),
                    ex.getMessage());
            emitTaskAudit("dispatch_rejected", task, "error_type=rejected");
            return false;
        } catch (Exception ex) {
            dispatchRejectCounter.increment();
            log.warn("Dispatch failed unexpectedly. taskId={}, owner={}, attempt={}, error={}",
                    task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), ex.getMessage());
            emitTaskAudit("dispatch_error", task, "error_type=" + classifyError(ex));
            return false;
        }
    }

    private void executeClaimedTask(AgentTaskEntity task) {
        inFlightTasks.incrementAndGet();
        recordClaimToStartLatency(task);
        if (auditLogEnabled && auditSuccessLogEnabled) {
            emitTaskAudit("execution_started", task, "-");
        }
        publishTaskEvent(PlanTaskEventTypeEnum.TASK_STARTED, task, buildTaskData(task));
        try {
            executeTask(task);
        } finally {
            inFlightTasks.decrementAndGet();
            releaseDispatchSlots(1);
        }
    }

    private void recordClaimToStartLatency(AgentTaskEntity task) {
        if (task == null || task.getClaimAt() == null) {
            return;
        }
        long latencyMs = Duration.between(task.getClaimAt(), LocalDateTime.now()).toMillis();
        if (latencyMs >= 0) {
            claimToStartLatencySummary.record(latencyMs);
        }
    }

    private void executeTask(AgentTaskEntity task) {
        long startedNanos = System.nanoTime();
        String outcome = "unknown";
        String errorType = "none";
        ScheduledFuture<?> heartbeatFuture = null;
        TaskExecutionEntity execution = null;

        try {
            if (task == null) {
                outcome = "skip_null_task";
                return;
            }
            if (!taskDispatchDomainService.hasValidClaim(task)) {
                outcome = "skip_invalid_claim";
                log.warn("Skip claimed task execution because claim metadata missing. taskId={}, claimOwner={}, attempt={}",
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt());
                return;
            }
            AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
            if (plan == null) {
                outcome = "skip_plan_not_found";
                log.warn("Skip task execution because plan not found. taskId={}, planId={}", task.getId(), task.getPlanId());
                return;
            }
            if (!plan.isExecutable()) {
                outcome = releaseClaimForNonExecutablePlan(task, plan)
                        ? "skip_plan_not_executable_released"
                        : "skip_plan_not_executable_release_failed";
                log.debug("Skip task execution because plan is not executable. planId={}, status={}, taskId={}",
                        plan.getId(), plan.getStatus(), task.getId());
                return;
            }

            recordRetryDistribution(task);
            boolean criticTask = isCriticTask(task);
            boolean refining = task.getCurrentRetry() != null && task.getCurrentRetry() > 0;
            heartbeatFuture = startHeartbeat(task);

            ChatResponse chatResponse;
            String response;
            int timeoutRetryCount = 0;
            while (true) {
                long startTime = System.currentTimeMillis();
                String prompt = criticTask ? buildCriticPrompt(task, plan)
                        : (refining ? buildRefinePrompt(task, plan) : buildPrompt(task, plan));
                execution = new TaskExecutionEntity();
                execution.setTaskId(task.getId());
                execution.setAttemptNumber(taskExecutionDomainService.resolveAttemptNumber(
                        taskExecutionRepository.getMaxAttemptNumber(task.getId()),
                        task
                ));
                execution.setPromptSnapshot(prompt);

                String systemPromptSuffix = buildRetrySystemPrompt(task);
                ChatClient taskClient = resolveTaskClient(task, plan, systemPromptSuffix);
                try {
                    chatResponse = callTaskClientWithTimeout(taskClient, prompt);
                } catch (TaskCallTimeoutException timeoutException) {
                    persistTimeoutExecution(execution, startTime, timeoutException);
                    boolean retrying = taskExecutionDomainService.canTimeoutRetry(task, timeoutRetryCount, executionTimeoutRetryMax);
                    recordTimeoutMetrics(task, retrying);
                    if (retrying) {
                        timeoutRetryCount++;
                        taskExecutionDomainService.applyTimeoutRetry(task, timeoutException.getMessage());
                        refining = true;
                        outcome = "timeout_retrying";
                        errorType = "timeout";
                        continue;
                    }
                    errorType = "timeout";
                    outcome = "failed";
                    task.fail(timeoutException.getMessage());
                    if (safeUpdateClaimedTask(task)) {
                        publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, buildTaskData(task));
                        publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                    }
                    log.warn("Task execution timed out and exhausted retries. taskId={}, nodeId={}, timeoutMs={}, retryMax={}",
                            task.getId(), task.getNodeId(), executionTimeoutMs, executionTimeoutRetryMax);
                    return;
                }

                response = extractContent(chatResponse);
                execution.setModelName(extractModelName(chatResponse));
                execution.setTokenUsage(extractTokenUsage(chatResponse));
                execution.setLlmResponseRaw(response);
                execution.setExecutionTime(startTime);
                break;
            }
            if (criticTask) {
                CriticDecision decision = parseCriticDecision(response);
                if (decision.pass) {
                    execution.markAsValid(decision.feedback);
                } else {
                    execution.markAsInvalid(decision.feedback);
                }
                taskExecutionRepository.save(execution);

                task.startValidation();
                if (decision.pass) {
                    task.complete(response);
                    boolean updated = safeUpdateClaimedTask(task);
                    outcome = updated ? "completed" : "update_guard_reject";
                    if (updated) {
                        publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, buildTaskData(task));
                        publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                    }
                } else {
                    task.setOutputResult(response);
                    task.resetToPending();
                    boolean updated = safeUpdateClaimedTask(task);
                    outcome = updated ? "critic_rejected" : "update_guard_reject";
                    if (updated) {
                        publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                    }
                    rollbackTarget(plan, task, decision.feedback);
                }
                return;
            }
            if (needsValidation(task)) {
                ValidationResult validation = evaluateValidation(task, response);
                if (validation.valid) {
                    execution.markAsValid(validation.feedback);
                } else {
                    execution.markAsInvalid(validation.feedback);
                }
                taskExecutionRepository.save(execution);

                task.startValidation();
                if (!validation.valid) {
                    handleValidationFailure(task, validation.feedback);
                    outcome = "validation_rejected";
                    return;
                }
                task.complete(response);
                if (safeUpdateClaimedTask(task)) {
                    syncBlackboard(plan, task, response);
                    outcome = "completed";
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, buildTaskData(task));
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                } else {
                    outcome = "update_guard_reject";
                }
            } else {
                execution.markAsValid("no validator");
                taskExecutionRepository.save(execution);

                task.startValidation();
                task.complete(response);
                if (safeUpdateClaimedTask(task)) {
                    syncBlackboard(plan, task, response);
                    outcome = "completed";
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, buildTaskData(task));
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                } else {
                    outcome = "update_guard_reject";
                }
            }
        } catch (Exception ex) {
            errorType = classifyError(ex);
            outcome = "failed";
            if (execution == null) {
                execution = new TaskExecutionEntity();
                execution.setTaskId(task == null ? null : task.getId());
            }
            execution.recordError(ex.getMessage());
            execution.setErrorType(errorType);
            execution.setExecutionTime(System.currentTimeMillis());
            safeSaveExecution(execution);

            if (task != null) {
                task.fail(ex.getMessage());
                if (safeUpdateClaimedTask(task)) {
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_COMPLETED, task, buildTaskData(task));
                    publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
                }
                log.warn("Task execution failed. taskId={}, nodeId={}, error={}",
                        task.getId(), task.getNodeId(), ex.getMessage());
            } else {
                log.warn("Task execution failed before task initialization. error={}", ex.getMessage());
            }
        } finally {
            stopHeartbeat(heartbeatFuture);
            recordExecutionMetrics(task, outcome, errorType, startedNanos);
        }
    }

    private void handleValidationFailure(AgentTaskEntity task, String feedback) {
        try {
            task.startRefining();
            safeUpdateClaimedTask(task);
        } catch (Exception ex) {
            String reason = StringUtils.isBlank(feedback) ? ex.getMessage() : feedback;
            task.fail("Validation failed: " + reason);
            safeUpdateClaimedTask(task);
        }
    }

    private boolean needsValidation(AgentTaskEntity task) {
        return taskEvaluationDomainService.needsValidation(task);
    }

    private ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
        TaskEvaluationDomainService.ValidationResult result = taskEvaluationDomainService.evaluateValidation(task, response);
        return new ValidationResult(result.valid(), result.feedback());
    }

    private boolean releaseClaimForNonExecutablePlan(AgentTaskEntity task, AgentPlanEntity plan) {
        if (task == null) {
            return false;
        }
        try {
            task.rollbackToDispatchQueue();
            boolean updated = safeUpdateClaimedTask(task);
            if (updated) {
                emitTaskAudit("claimed_release_non_executable_plan", task,
                        "plan_status=" + (plan == null || plan.getStatus() == null ? "null" : plan.getStatus().name()));
                publishTaskEvent(PlanTaskEventTypeEnum.TASK_LOG, task, buildTaskLog(task));
            }
            return updated;
        } catch (Exception ex) {
            log.warn("Failed to release claimed task for non-executable plan. taskId={}, planId={}, error={}",
                    task.getId(), task.getPlanId(), ex.getMessage());
            emitTaskAudit("claimed_release_non_executable_plan_failed", task, "error_type=" + classifyError(ex));
            return false;
        }
    }


    private ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan) {
        return resolveTaskClient(task, plan, null);
    }

    /**
     * 为当前 Task 解析执行用 TaskClient（底层为 ChatClient）。
     * 优先级：task config(agentId/agentKey) > fallback key 列表 > 首个激活 AgentProfile。
     */
    private ChatClient resolveTaskClient(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix) {
        TaskAgentSelectionDomainService.SelectionPlan selectionPlan =
                taskAgentSelectionDomainService.resolveSelectionPlan(task, workerFallbackAgentKeys, criticFallbackAgentKeys);
        String conversationId = buildConversationId(plan, task);

        if (selectionPlan.configuredAgentId() != null) {
            return agentFactory.createAgent(selectionPlan.configuredAgentId(), conversationId, systemPromptSuffix);
        }
        if (StringUtils.isNotBlank(selectionPlan.configuredAgentKey())) {
            return agentFactory.createAgent(selectionPlan.configuredAgentKey(), conversationId, systemPromptSuffix);
        }

        List<String> fallbackKeys = selectionPlan.fallbackKeys();
        for (String fallbackKey : fallbackKeys) {
            ChatClient fallbackTaskClient = tryCreateTaskClientByAgentKey(fallbackKey, conversationId, systemPromptSuffix);
            if (fallbackTaskClient != null) {
                if (taskAgentSelectionDomainService.shouldWarnFallbackKey(fallbackKey)) {
                    log.warn("Task fallback agent profile key applied. taskId={}, nodeId={}, taskType={}, agentKey={}",
                            task.getId(), task.getNodeId(), task.getTaskType(), fallbackKey);
                }
                return fallbackTaskClient;
            }
        }

        AgentRegistryEntity defaultAgentProfile = resolveDefaultActiveAgentProfile();
        if (defaultAgentProfile != null) {
            log.warn("Task fallback to first active agent profile. taskId={}, nodeId={}, taskType={}, agentKey={}",
                    task.getId(), task.getNodeId(), task.getTaskType(), defaultAgentProfile.getKey());
            return agentFactory.createAgent(defaultAgentProfile, conversationId, systemPromptSuffix);
        }

        throw new IllegalStateException("No available active agent profile for task fallback. triedKeys=" + fallbackKeys);
    }

    private ChatClient tryCreateTaskClientByAgentKey(String agentKey, String conversationId, String systemPromptSuffix) {
        if (StringUtils.isBlank(agentKey)) {
            return null;
        }
        try {
            return agentFactory.createAgent(agentKey, conversationId, systemPromptSuffix);
        } catch (IllegalStateException ex) {
            if (taskAgentSelectionDomainService.isIgnorableCreateError(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private AgentRegistryEntity resolveDefaultActiveAgentProfile() {
        long now = System.currentTimeMillis();
        AgentRegistryEntity cached = cachedDefaultAgent;
        if (cached != null && now - cachedDefaultAgentAtMillis <= defaultAgentCacheTtlMs) {
            return cached;
        }
        List<AgentRegistryEntity> activeAgents = agentRegistryRepository.findByActive(true);
        if (activeAgents == null || activeAgents.isEmpty()) {
            cachedDefaultAgent = null;
            cachedDefaultAgentAtMillis = now;
            return null;
        }
        AgentRegistryEntity selected = taskAgentSelectionDomainService.selectDefaultActiveAgent(activeAgents);
        cachedDefaultAgent = selected;
        cachedDefaultAgentAtMillis = now;
        return selected;
    }


    private String buildConversationId(AgentPlanEntity plan, AgentTaskEntity task) {
        String planPart = plan.getId() == null ? "plan" : "plan-" + plan.getId();
        String nodePart = StringUtils.defaultIfBlank(task.getNodeId(), "node");
        return planPart + ":" + nodePart;
    }

    private String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        return taskPromptDomainService.buildWorkerPrompt(task, plan, this::toJson);
    }

    private String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        String targetNodeId = taskPromptDomainService.resolveTargetNodeId(task);
        AgentTaskEntity targetTask = targetNodeId == null ? null
                : agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        String targetOutput = targetTask == null ? "" : StringUtils.defaultString(targetTask.getOutputResult());
        return taskPromptDomainService.buildCriticPrompt(task, plan, targetNodeId, targetOutput, this::toJson);
    }


    private String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        TaskExecutionEntity lastExecution = loadLastExecution(task.getId());
        String lastResponse = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getLlmResponseRaw());
        String feedback = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getValidationFeedback());
        String basePrompt = buildPrompt(task, plan);
        return taskPromptDomainService.buildRefinePrompt(basePrompt, lastResponse, feedback);
    }

    private TaskExecutionEntity loadLastExecution(Long taskId) {
        if (taskId == null) {
            return null;
        }
        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);
        if (executions == null || executions.isEmpty()) {
            return null;
        }
        return executions.get(0);
    }

    private String buildRetrySystemPrompt(AgentTaskEntity task) {
        return taskPromptDomainService.buildRetrySystemPrompt(task);
    }


    private CriticDecision parseCriticDecision(String response) {
        Map<String, Object> payload = parseJsonPayload(StringUtils.defaultString(response).trim());
        TaskEvaluationDomainService.CriticDecision decision = taskEvaluationDomainService.parseCriticDecision(response, payload);
        return new CriticDecision(decision.pass(), decision.feedback());
    }

    private Map<String, Object> parseJsonPayload(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            String snippet = text.substring(start, end + 1);
            try {
                return objectMapper.readValue(snippet, MAP_TYPE);
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
    }

    private void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback) {
        String targetNodeId = resolveTargetNodeId(criticTask);
        if (StringUtils.isBlank(targetNodeId)) {
            log.warn("Critic rollback skipped: target node not found. planId={}, taskId={}", plan.getId(), criticTask.getId());
            return;
        }

        AgentTaskEntity target = agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        TaskRecoveryDomainService.RecoveryDecision decision =
                taskRecoveryDomainService.applyCriticFeedback(target, feedback);

        if (decision == TaskRecoveryDomainService.RecoveryDecision.NOT_FOUND) {
            log.warn("Critic rollback skipped: target task not found. planId={}, nodeId={}", plan.getId(), targetNodeId);
            return;
        }
        if (decision == TaskRecoveryDomainService.RecoveryDecision.ALREADY_FAILED) {
            return;
        }
        if (decision.requiresUpdate()) {
            safeUpdateTask(target);
        }
    }

    private String resolveTargetNodeId(AgentTaskEntity task) {
        return taskPromptDomainService.resolveTargetNodeId(task);
    }

    private boolean isCriticTask(AgentTaskEntity task) {
        return task != null && task.getTaskType() == TaskTypeEnum.CRITIC;
    }


    private void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output) {
        if (plan == null || plan.getId() == null || task == null) {
            return;
        }
        Map<String, Object> config = task.getConfigSnapshot();
        Map<String, Object> delta = new HashMap<>();

        boolean merge = getBoolean(config, "mergeOutput", "merge_output", "outputMerge");
        if (merge) {
            Map<String, Object> parsed = parseJsonMap(output);
            if (parsed != null && !parsed.isEmpty()) {
                delta.putAll(parsed);
            }
        }

        if (delta.isEmpty()) {
            String outputKey = getString(config, "outputKey", "output_key", "resultKey", "result_key");
            if (StringUtils.isBlank(outputKey)) {
                outputKey = StringUtils.defaultIfBlank(task.getNodeId(), "output");
            }
            delta.put(outputKey, output);
        }

        for (int attempt = 1; attempt <= PLAN_CONTEXT_UPDATE_MAX_RETRY; attempt++) {
            AgentPlanEntity latestPlan = agentPlanRepository.findById(plan.getId());
            if (latestPlan == null) {
                log.warn("Failed to update plan context because plan not found. planId={}", plan.getId());
                return;
            }
            Map<String, Object> mergedContext = latestPlan.getGlobalContext() == null
                    ? new HashMap<>()
                    : new HashMap<>(latestPlan.getGlobalContext());
            mergedContext.putAll(delta);
            latestPlan.setGlobalContext(mergedContext);
            if (safeUpdatePlan(latestPlan, attempt, PLAN_CONTEXT_UPDATE_MAX_RETRY)) {
                plan.setGlobalContext(mergedContext);
                plan.setVersion(latestPlan.getVersion());
                return;
            }
        }
    }


    private String getString(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }


    private boolean getBoolean(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return false;
    }

    private Map<String, Object> parseJsonMap(String output) {
        if (StringUtils.isBlank(output)) {
            return null;
        }
        String trimmed = output.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }

    private void recordRetryDistribution(AgentTaskEntity task) {
        if (task == null) {
            return;
        }
        int retry = task.getCurrentRetry() == null ? 0 : Math.max(task.getCurrentRetry(), 0);
        executionRetrySummary.record(retry);
    }

    private ChatResponse callTaskClientWithTimeout(ChatClient taskClient, String prompt) {
        Future<ChatResponse> future = taskCallExecutor.submit(() -> {
            ChatClient.CallResponseSpec callResponse = taskClient.prompt(prompt).call();
            return callResponse == null ? null : callResponse.chatResponse();
        });
        try {
            return future.get(executionTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TaskCallTimeoutException("Task execution timed out after " + executionTimeoutMs + " ms", ex);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task execution interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause == null ? "Task execution failed" : cause.getMessage(), cause);
        }
    }

    private void persistTimeoutExecution(TaskExecutionEntity execution,
                                         long startTime,
                                         TaskCallTimeoutException timeoutException) {
        if (execution == null) {
            return;
        }
        execution.recordError(timeoutException.getMessage());
        execution.markAsInvalid(timeoutException.getMessage());
        execution.setErrorType("timeout");
        execution.setExecutionTime(startTime);
        safeSaveExecution(execution);
    }

    private void recordTimeoutMetrics(AgentTaskEntity task, boolean retrying) {
        String taskType = resolveTaskType(task);
        meterRegistry.counter("agent.task.execution.timeout.total",
                "task_type", taskType).increment();
        if (retrying) {
            meterRegistry.counter("agent.task.execution.timeout.retry.total",
                    "task_type", taskType).increment();
        } else {
            meterRegistry.counter("agent.task.execution.timeout.final_fail.total",
                    "task_type", taskType).increment();
        }
    }

    private void recordExecutionMetrics(AgentTaskEntity task, String outcome, String errorType, long startedNanos) {
        String result = StringUtils.defaultIfBlank(outcome, "unknown");
        String taskType = resolveTaskType(task);
        long durationNanos = Math.max(0L, System.nanoTime() - startedNanos);
        Timer.builder(METRIC_EXECUTION_DURATION)
                .tag("result", result)
                .tag("task_type", taskType)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        meterRegistry.counter(METRIC_EXECUTION_TOTAL,
                "result", result,
                "task_type", taskType).increment();
        if ("failed".equals(result)) {
            meterRegistry.counter(METRIC_EXECUTION_FAILURE_TOTAL,
                    "task_type", taskType,
                    "error_type", StringUtils.defaultIfBlank(errorType, "unknown")).increment();
        }
        if (auditLogEnabled && shouldEmitAudit(result)) {
            emitTaskAudit("execution_" + result, task, "error_type=" + StringUtils.defaultIfBlank(errorType, "none"));
        }
    }

    private boolean shouldEmitAudit(String result) {
        if (auditSuccessLogEnabled) {
            return true;
        }
        return "failed".equals(result)
                || "validation_rejected".equals(result)
                || "critic_rejected".equals(result)
                || "update_guard_reject".equals(result);
    }

    private String resolveTaskType(AgentTaskEntity task) {
        if (task == null || task.getTaskType() == null) {
            return "unknown";
        }
        return task.getTaskType().name().toLowerCase();
    }

    private String classifyError(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String type = throwable.getClass().getSimpleName();
        String message = StringUtils.defaultString(throwable.getMessage()).toLowerCase();
        if (message.contains("optimistic lock")) {
            return "optimistic_lock";
        }
        if (message.contains("timeout")) {
            return "timeout";
        }
        if (message.contains("connection") || message.contains("jdbc")
                || message.contains("sql")) {
            return "db_error";
        }
        if (type.contains("Json") || message.contains("json")) {
            return "json_error";
        }
        return "runtime_error";
    }

    private String extractModelName(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        return chatResponse.getMetadata().getModel();
    }

    private Map<String, Object> extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return null;
        }
        Map<String, Object> tokenUsage = new HashMap<>();
        if (usage.getPromptTokens() != null) {
            tokenUsage.put("prompt_tokens", usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            tokenUsage.put("completion_tokens", usage.getCompletionTokens());
        }
        if (usage.getTotalTokens() != null) {
            tokenUsage.put("total_tokens", usage.getTotalTokens());
        }
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage != null) {
            tokenUsage.put("native_usage", normalizeNativeUsage(nativeUsage));
        }
        return tokenUsage.isEmpty() ? null : tokenUsage;
    }

    private Object normalizeNativeUsage(Object nativeUsage) {
        if (nativeUsage == null) {
            return null;
        }
        if (nativeUsage instanceof Map<?, ?>) {
            return nativeUsage;
        }
        try {
            return objectMapper.convertValue(nativeUsage, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            return String.valueOf(nativeUsage);
        }
    }

    private String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        return StringUtils.defaultString(chatResponse.getResult().getOutput().getText());
    }

    private Map<String, Object> buildTaskData(AgentTaskEntity task) {
        Map<String, Object> data = new HashMap<>();
        if (task == null) {
            return data;
        }
        data.put("planId", task.getPlanId());
        data.put("taskId", task.getId());
        data.put("nodeId", task.getNodeId());
        data.put("status", task.getStatus() == null ? null : task.getStatus().name());
        data.put("taskType", task.getTaskType() == null ? null : task.getTaskType().name());
        return data;
    }

    private Map<String, Object> buildTaskLog(AgentTaskEntity task) {
        Map<String, Object> data = buildTaskData(task);
        data.put("output", task == null ? null : task.getOutputResult());
        return data;
    }

    private void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data) {
        if (eventType == null || task == null || task.getPlanId() == null) {
            return;
        }
        try {
            planTaskEventPublisher.publish(eventType,
                    task.getPlanId(),
                    task.getId(),
                    data == null ? Collections.emptyMap() : data);
        } catch (Exception ex) {
            log.warn("Failed to publish task event. planId={}, taskId={}, type={}, error={}",
                    task.getPlanId(), task.getId(), eventType, ex.getMessage());
        }
    }

    private void emitTaskAudit(String event, AgentTaskEntity task, String detail) {
        if (!auditLogEnabled || StringUtils.isBlank(event)) {
            return;
        }
        String normalizedDetail = StringUtils.defaultIfBlank(detail, "-");
        if (task == null) {
            log.info("TASK_AUDIT event={}, taskId=null, planId=null, nodeId=null, owner={}, attempt=null, detail={}",
                    event, claimOwner, normalizedDetail);
            return;
        }
        log.info("TASK_AUDIT event={}, taskId={}, planId={}, nodeId={}, owner={}, attempt={}, detail={}",
                event,
                task.getId(),
                task.getPlanId(),
                task.getNodeId(),
                StringUtils.defaultIfBlank(task.getClaimOwner(), claimOwner),
                task.getExecutionAttempt(),
                normalizedDetail);
    }

    private static final class ValidationResult {
        private final boolean valid;
        private final String feedback;

        private ValidationResult(boolean valid, String feedback) {
            this.valid = valid;
            this.feedback = feedback;
        }
    }

    private static final class CriticDecision {
        private final boolean pass;
        private final String feedback;

        private CriticDecision(boolean pass, String feedback) {
            this.pass = pass;
            this.feedback = feedback;
        }
    }

    private boolean safeUpdateTask(AgentTaskEntity task) {
        try {
            agentTaskRepository.update(task);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to update task status. taskId={}, error={}", task.getId(), ex.getMessage());
            return false;
        }
    }

    private boolean safeUpdateClaimedTask(AgentTaskEntity task) {
        try {
            boolean updated = agentTaskRepository.updateClaimedTaskState(task);
            if (!updated) {
                claimedUpdateGuardRejectCounter.increment();
                log.debug("Claimed task update skipped by ownership/attempt guard. taskId={}, owner={}, attempt={}, status={}",
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), task.getStatus());
                emitTaskAudit("claimed_update_guard_reject", task,
                        "status=" + (task.getStatus() == null ? "null" : task.getStatus().name()));
            } else {
                claimedUpdateSuccessCounter.increment();
            }
            return updated;
        } catch (Exception ex) {
            claimedUpdateErrorCounter.increment();
            log.warn("Failed to update claimed task. taskId={}, owner={}, attempt={}, error={}",
                    task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), ex.getMessage());
            emitTaskAudit("claimed_update_error", task, "error_type=" + classifyError(ex));
            return false;
        }
    }

    private ScheduledFuture<?> startHeartbeat(AgentTaskEntity task) {
        if (task == null || task.getId() == null || StringUtils.isBlank(task.getClaimOwner())
                || task.getExecutionAttempt() == null || claimHeartbeatSeconds <= 0) {
            return null;
        }
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                boolean renewed = agentTaskRepository.renewClaimLease(
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), claimLeaseSeconds);
                if (!renewed) {
                    heartbeatGuardRejectCounter.increment();
                    log.debug("Claim lease renew skipped. taskId={}, owner={}, attempt={}",
                            task.getId(), task.getClaimOwner(), task.getExecutionAttempt());
                    emitTaskAudit("lease_renew_guard_reject", task, "renewed=false");
                } else {
                    heartbeatSuccessCounter.increment();
                }
            } catch (Exception ex) {
                heartbeatErrorCounter.increment();
                log.warn("Failed to renew claim lease. taskId={}, owner={}, attempt={}, error={}",
                        task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), ex.getMessage());
                emitTaskAudit("lease_renew_error", task, "error_type=" + classifyError(ex));
            }
        }, claimHeartbeatSeconds, claimHeartbeatSeconds, TimeUnit.SECONDS);
    }

    private void stopHeartbeat(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdownExecutors() {
        heartbeatScheduler.shutdownNow();
        taskCallExecutor.shutdownNow();
    }

    private String resolveInstanceId(String configuredInstanceId) {
        if (StringUtils.isNotBlank(configuredInstanceId)) {
            return configuredInstanceId.trim();
        }
        String host = System.getenv("HOSTNAME");
        if (StringUtils.isBlank(host)) {
            host = "local";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        return host + ":" + pid;
    }

    private void emitExpiredRunningMetric() {
        long tick = monitorTick.incrementAndGet();
        if (tick % 30 != 0) {
            return;
        }
        try {
            long count = agentTaskRepository.countExpiredRunningTasks();
            expiredRunningGauge.set(count);
            if (count > 0) {
                expiredRunningDetectedCounter.increment();
                log.warn("Detected expired running tasks. count={}", count);
            } else {
                log.debug("Expired running tasks check. count=0");
            }
        } catch (Exception ex) {
            expiredRunningCheckErrorCounter.increment();
            log.warn("Failed to check expired running tasks. error={}", ex.getMessage());
        }
    }

    private void safeSaveExecution(TaskExecutionEntity execution) {
        try {
            taskExecutionRepository.save(execution);
        } catch (Exception ex) {
            log.warn("Failed to save task execution. taskId={}, error={}", execution.getTaskId(), ex.getMessage());
        }
    }

    private boolean safeUpdatePlan(AgentPlanEntity plan, int attempt, int maxAttempts) {
        if (plan == null || plan.getId() == null) {
            return false;
        }
        try {
            agentPlanRepository.update(plan);
            return true;
        } catch (Exception ex) {
            boolean optimisticLock = ex.getMessage() != null && ex.getMessage().contains("Optimistic lock");
            if (optimisticLock) {
                if (attempt < maxAttempts) {
                    log.debug("Retry updating plan context after optimistic lock. planId={}, attempt={}/{}",
                            plan.getId(), attempt, maxAttempts);
                } else {
                    log.warn("Failed to update plan context after retries. planId={}, attempts={}, error={}",
                            plan.getId(), maxAttempts, ex.getMessage());
                }
                return false;
            }
            log.warn("Failed to update plan context. planId={}, error={}", plan.getId(), ex.getMessage());
            return false;
        }
    }

    private static final class TaskCallTimeoutException extends RuntimeException {
        private TaskCallTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
