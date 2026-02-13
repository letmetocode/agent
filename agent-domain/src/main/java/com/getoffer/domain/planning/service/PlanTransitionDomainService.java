package com.getoffer.domain.planning.service;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.types.enums.PlanStatusEnum;
import org.springframework.stereotype.Service;

/**
 * Plan 状态推进领域服务：根据任务聚合统计结果决定 Plan 目标状态并执行迁移。
 */
@Service
public class PlanTransitionDomainService {

    public PlanAggregateStatus resolveAggregateStatus(PlanTaskStatusStat stat) {
        if (stat == null || valueOf(stat.getTotal()) <= 0) {
            return PlanAggregateStatus.NONE;
        }

        long failedCount = valueOf(stat.getFailedCount());
        if (failedCount > 0) {
            return PlanAggregateStatus.FAILED;
        }

        long runningLikeCount = valueOf(stat.getRunningLikeCount());
        if (runningLikeCount > 0) {
            return PlanAggregateStatus.RUNNING;
        }

        long total = valueOf(stat.getTotal());
        long terminalCount = valueOf(stat.getTerminalCount());
        if (terminalCount == total) {
            return PlanAggregateStatus.COMPLETED;
        }
        return PlanAggregateStatus.READY;
    }

    public PlanStatusEnum resolveTargetStatus(PlanStatusEnum currentStatus, PlanAggregateStatus aggregateStatus) {
        if (currentStatus == null || aggregateStatus == null || aggregateStatus == PlanAggregateStatus.NONE) {
            return null;
        }
        if (currentStatus == PlanStatusEnum.COMPLETED
                || currentStatus == PlanStatusEnum.FAILED
                || currentStatus == PlanStatusEnum.CANCELLED) {
            return null;
        }

        return switch (aggregateStatus) {
            case RUNNING -> currentStatus == PlanStatusEnum.READY ? PlanStatusEnum.RUNNING : null;
            case COMPLETED -> (currentStatus == PlanStatusEnum.READY || currentStatus == PlanStatusEnum.RUNNING)
                    ? PlanStatusEnum.COMPLETED : null;
            case FAILED -> (currentStatus == PlanStatusEnum.READY || currentStatus == PlanStatusEnum.RUNNING)
                    ? PlanStatusEnum.FAILED : null;
            case READY, NONE -> null;
        };
    }

    public void transitPlan(AgentPlanEntity plan, PlanStatusEnum targetStatus) {
        if (plan == null || targetStatus == null) {
            return;
        }
        if (targetStatus == PlanStatusEnum.RUNNING) {
            plan.startExecution();
            return;
        }
        if (targetStatus == PlanStatusEnum.COMPLETED) {
            plan.completeFromReadyOrRunning();
            return;
        }
        if (targetStatus == PlanStatusEnum.FAILED) {
            plan.fail("Task aggregate detected FAILED");
        }
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    public enum PlanAggregateStatus {
        NONE,
        READY,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
