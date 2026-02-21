package com.getoffer.trigger.job;

import com.getoffer.trigger.application.command.PlanningTurnRecoveryApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 规划超时回合恢复守护进程：对悬挂在 PLANNING 的回合执行兜底收敛。
 */
@Slf4j
@Component
public class PlanningTurnRecoveryJob {

    private final PlanningTurnRecoveryApplicationService planningTurnRecoveryApplicationService;
    private final int batchSize;
    private final int timeoutMinutes;

    public PlanningTurnRecoveryJob(PlanningTurnRecoveryApplicationService planningTurnRecoveryApplicationService,
                                   @Value("${chat.planning-recovery.batch-size:100}") int batchSize,
                                   @Value("${chat.planning-recovery.timeout-minutes:2}") int timeoutMinutes) {
        this.planningTurnRecoveryApplicationService = planningTurnRecoveryApplicationService;
        this.batchSize = batchSize > 0 ? batchSize : 100;
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : 2;
    }

    @Scheduled(fixedDelayString = "${chat.planning-recovery.poll-interval-ms:30000}", scheduler = "daemonScheduler")
    public void recoverStalePlanningTurns() {
        PlanningTurnRecoveryApplicationService.RecoveryResult result =
                planningTurnRecoveryApplicationService.recoverStalePlanningTurns(batchSize, timeoutMinutes);
        if (result.processedCount() <= 0) {
            return;
        }
        log.info("Planning turn recovery round finished. processed={}, recovered={}, dedup={}, errors={}",
                result.processedCount(),
                result.recoveredCount(),
                result.dedupCount(),
                result.errorCount());
    }
}
