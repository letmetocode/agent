package com.getoffer.trigger.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.domain.task.service.TaskJsonDomainService;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task executor: run READY tasks, write results, and sync blackboard.
 */
@Slf4j
@Component
public class TaskExecutor {

    private static final String METRIC_EXECUTION_TOTAL = "agent.task.execution.total";
    private static final String METRIC_EXECUTION_DURATION = "agent.task.execution.duration";
    private static final String METRIC_EXECUTION_FAILURE_TOTAL = "agent.task.execution.failure.total";
    private static final int PLAN_CONTEXT_UPDATE_MAX_RETRY = 3;

    private final IAgentTaskRepository agentTaskRepository;
    private final TaskDispatchDomainService taskDispatchDomainService;
    private final String claimOwner;
    private final int claimBatchSize;
    private final int claimLeaseSeconds;
    private final int claimMaxPerTick;
    private final boolean claimReadyFirst;
    private final double refiningMaxRatio;
    private final int refiningMinPerTick;
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
    private final TaskExecutionRuntimeSupport taskExecutionRuntimeSupport;
    private final TaskExecutionRunner taskExecutionRunner;
    private final TaskExecutionRunner.CallSupport callSupport;
    private final TaskExecutionRunner.EvaluationSupport evaluationSupport;
    private final TaskExecutionRunner.PersistenceSupport persistenceSupport;

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
                        TaskBlackboardDomainService taskBlackboardDomainService,
                        TaskJsonDomainService taskJsonDomainService,
                        TaskPersistenceApplicationService taskPersistenceApplicationService,
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
        this.taskDispatchDomainService = taskDispatchDomainService;
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
        int normalizedClaimHeartbeatSeconds = claimHeartbeatSeconds > 0 ? claimHeartbeatSeconds : 30;
        int normalizedExecutionTimeoutMs = executionTimeoutMs > 0 ? executionTimeoutMs : 120000;
        int normalizedExecutionTimeoutRetryMax = Math.max(executionTimeoutRetryMax, 0);
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
        List<String> normalizedWorkerFallbackAgentKeys =
                taskAgentSelectionDomainService.parseFallbackAgentKeys(workerFallbackAgentKeys, "worker", "assistant");
        List<String> normalizedCriticFallbackAgentKeys =
                taskAgentSelectionDomainService.parseFallbackAgentKeys(criticFallbackAgentKeys, "critic", "assistant");
        long normalizedDefaultAgentCacheTtlMs = defaultAgentCacheTtlMs > 0 ? defaultAgentCacheTtlMs : 30000L;
        this.taskExecutionRuntimeSupport = new TaskExecutionRuntimeSupport(
                agentTaskRepository,
                planTaskEventPublisher,
                taskPersistenceApplicationService,
                objectMapper,
                meterRegistry,
                taskCallExecutor,
                heartbeatScheduler,
                this.claimLeaseSeconds,
                normalizedClaimHeartbeatSeconds,
                normalizedExecutionTimeoutMs,
                executionRetrySummary,
                heartbeatSuccessCounter,
                heartbeatGuardRejectCounter,
                heartbeatErrorCounter,
                claimedUpdateSuccessCounter,
                claimedUpdateGuardRejectCounter,
                claimedUpdateErrorCounter,
                this.claimOwner,
                auditLogEnabled,
                auditSuccessLogEnabled
        );
        this.taskExecutionRunner = new TaskExecutionRunner();
        TaskExecutionClientResolver taskExecutionClientResolver = new TaskExecutionClientResolver(
                agentFactory,
                agentRegistryRepository,
                taskAgentSelectionDomainService,
                normalizedWorkerFallbackAgentKeys,
                normalizedCriticFallbackAgentKeys,
                normalizedDefaultAgentCacheTtlMs,
                planTaskEventPublisher
        );
        TaskExecutionFlowSupport taskExecutionFlowSupport = new TaskExecutionFlowSupport(
                this.agentTaskRepository,
                taskExecutionRepository,
                taskPromptDomainService,
                taskEvaluationDomainService,
                taskRecoveryDomainService,
                taskBlackboardDomainService,
                taskJsonDomainService,
                taskPersistenceApplicationService,
                objectMapper,
                PLAN_CONTEXT_UPDATE_MAX_RETRY
        );
        this.callSupport = new TaskExecutionCallSupportAdapter(
                this.taskExecutionRuntimeSupport,
                taskDispatchDomainService,
                agentPlanRepository,
                taskExecutionDomainService,
                taskExecutionRepository,
                normalizedExecutionTimeoutRetryMax,
                taskExecutionFlowSupport,
                taskExecutionClientResolver
        );
        this.evaluationSupport = new TaskExecutionEvaluationSupportAdapter(
                taskEvaluationDomainService,
                taskExecutionFlowSupport,
                this.taskExecutionRuntimeSupport
        );
        this.persistenceSupport = new TaskExecutionPersistenceSupportAdapter(this.taskExecutionRuntimeSupport);
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
            taskExecutionRuntimeSupport.auditClaimReclaimed(task);
        } else {
            taskExecutionRuntimeSupport.auditClaimAcquired(task);
        }
    }

    private boolean dispatchClaimedTask(AgentTaskEntity task) {
        if (task == null) {
            return false;
        }
        try {
            taskExecutionWorker.execute(() -> executeClaimedTask(task));
            dispatchSuccessCounter.increment();
            taskExecutionRuntimeSupport.auditDispatchSubmitted(task);
            return true;
        } catch (RejectedExecutionException ex) {
            dispatchRejectCounter.increment();
            log.warn("Dispatch rejected by worker executor. taskId={}, owner={}, attempt={}, active={}, queueSize={}, error={}",
                    task.getId(), task.getClaimOwner(), task.getExecutionAttempt(),
                    taskExecutionWorker.getActiveCount(),
                    taskExecutionWorker.getQueue().size(),
                    ex.getMessage());
            taskExecutionRuntimeSupport.auditDispatchRejected(task);
            return false;
        } catch (Exception ex) {
            dispatchRejectCounter.increment();
            log.warn("Dispatch failed unexpectedly. taskId={}, owner={}, attempt={}, error={}",
                    task.getId(), task.getClaimOwner(), task.getExecutionAttempt(), ex.getMessage());
            taskExecutionRuntimeSupport.auditDispatchError(task, ex);
            return false;
        }
    }

    private void executeClaimedTask(AgentTaskEntity task) {
        inFlightTasks.incrementAndGet();
        recordClaimToStartLatency(task);
        taskExecutionRuntimeSupport.auditExecutionStarted(task);
        taskExecutionRuntimeSupport.publishTaskStarted(task);
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
        try {
            TaskExecutionRunner.ExecutionResult result = taskExecutionRunner.run(
                    task,
                    callSupport,
                    evaluationSupport,
                    persistenceSupport
            );
            if (result != null) {
                outcome = taskExecutionRuntimeSupport.normalizeExecutionResult(result.outcome());
                errorType = taskExecutionRuntimeSupport.normalizeAuditErrorType(result.errorType());
            }
        } catch (Exception ex) {
            outcome = "failed";
            errorType = taskExecutionRuntimeSupport.classifyError(ex);
            log.warn("Task execution runner failed unexpectedly. taskId={}, error={}",
                    task == null ? null : task.getId(), ex.getMessage());
        } finally {
            recordExecutionMetrics(task, outcome, errorType, startedNanos);
        }
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }

    private void recordExecutionMetrics(AgentTaskEntity task, String outcome, String errorType, long startedNanos) {
        String result = taskExecutionRuntimeSupport.normalizeExecutionResult(outcome);
        String taskType = taskExecutionRuntimeSupport.resolveTaskType(task);
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
                    "error_type", taskExecutionRuntimeSupport.normalizeFailureMetricErrorType(errorType)).increment();
        }
        taskExecutionRuntimeSupport.auditExecutionResult(task, result, errorType);
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

}
