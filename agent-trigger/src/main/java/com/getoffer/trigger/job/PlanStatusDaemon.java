package com.getoffer.trigger.job;

import com.getoffer.trigger.application.command.PlanStatusSyncApplicationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Plan 状态推进守护进程：由 Task 聚合状态驱动 Plan 状态闭环。
 */
@Slf4j
@Component
public class PlanStatusDaemon {

    private final PlanStatusSyncApplicationService planStatusSyncApplicationService;
    private final int batchSize;
    private final int maxPlansPerRound;
    private final Counter finalizeAttemptCounter;
    private final Counter finalizeDedupCounter;
    private final Counter finishedPublishCounter;

    public PlanStatusDaemon(PlanStatusSyncApplicationService planStatusSyncApplicationService,
                            @Value("${plan-status.batch-size:200}") int batchSize,
                            @Value("${plan-status.max-plans-per-round:1000}") int maxPlansPerRound) {
        this.planStatusSyncApplicationService = planStatusSyncApplicationService;
        this.batchSize = batchSize > 0 ? batchSize : 200;
        this.maxPlansPerRound = maxPlansPerRound > 0 ? maxPlansPerRound : 1000;
        this.finalizeAttemptCounter = Counter.builder("agent.plan.finalize.attempt.total").register(Metrics.globalRegistry);
        this.finalizeDedupCounter = Counter.builder("agent.plan.finalize.dedup.total").register(Metrics.globalRegistry);
        this.finishedPublishCounter = Counter.builder("agent.plan.finished.publish.total").register(Metrics.globalRegistry);
    }

    @Scheduled(fixedDelayString = "${plan-status.poll-interval-ms:1000}", scheduler = "daemonScheduler")
    public void syncPlanStatuses() {
        PlanStatusSyncApplicationService.SyncResult result =
                planStatusSyncApplicationService.syncPlanStatuses(batchSize, maxPlansPerRound);
        if (result.processedCount() <= 0) {
            return;
        }

        if (result.finalizeAttemptCount() > 0) {
            finalizeAttemptCounter.increment(result.finalizeAttemptCount());
        }
        if (result.finalizeDedupCount() > 0) {
            finalizeDedupCounter.increment(result.finalizeDedupCount());
        }
        if (result.finishedPublishCount() > 0) {
            finishedPublishCounter.increment(result.finishedPublishCount());
        }

        log.debug("Plan status sync round finished. processed={}, advanced={}, finalized={}, dedup={}, published={}, errors={}",
                result.processedCount(),
                result.advancedCount(),
                result.finalizeAttemptCount(),
                result.finalizeDedupCount(),
                result.finishedPublishCount(),
                result.errorCount());
    }
}
