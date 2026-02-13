package com.getoffer.trigger.application.command;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 状态同步写用例：统一承载 Plan 聚合推进、终态 finalize 与事件发布。
 */
@Slf4j
@Service
public class PlanStatusSyncApplicationService {

    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int DEFAULT_MAX_PLANS_PER_ROUND = 1000;

    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final TurnFinalizeApplicationService turnFinalizeApplicationService;
    private final PlanTransitionDomainService planTransitionDomainService;

    public PlanStatusSyncApplicationService(IAgentPlanRepository agentPlanRepository,
                                            IAgentTaskRepository agentTaskRepository,
                                            PlanTaskEventPublisher planTaskEventPublisher,
                                            TurnFinalizeApplicationService turnFinalizeApplicationService,
                                            PlanTransitionDomainService planTransitionDomainService) {
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.turnFinalizeApplicationService = turnFinalizeApplicationService;
        this.planTransitionDomainService = planTransitionDomainService;
    }

    public SyncResult syncPlanStatuses(int batchSize, int maxPlansPerRound) {
        int normalizedBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        int normalizedMaxPlans = maxPlansPerRound > 0 ? maxPlansPerRound : DEFAULT_MAX_PLANS_PER_ROUND;

        List<AgentPlanEntity> readyPlans = loadPlansByStatus(PlanStatusEnum.READY,
                normalizedMaxPlans,
                normalizedBatchSize);
        int remaining = Math.max(0, normalizedMaxPlans - readyPlans.size());
        List<AgentPlanEntity> runningPlans = remaining > 0
                ? loadPlansByStatus(PlanStatusEnum.RUNNING, remaining, normalizedBatchSize)
                : Collections.emptyList();
        List<AgentPlanEntity> plans = mergePlans(readyPlans, runningPlans);
        if (plans.isEmpty()) {
            return SyncResult.empty();
        }

        SyncStats syncStats = new SyncStats();
        for (int index = 0; index < plans.size(); index += normalizedBatchSize) {
            int end = Math.min(index + normalizedBatchSize, plans.size());
            List<AgentPlanEntity> batch = plans.subList(index, end);
            processBatch(batch, syncStats);
        }

        return syncStats.toResult();
    }

    private List<AgentPlanEntity> loadPlansByStatus(PlanStatusEnum status,
                                                     int maxCount,
                                                     int batchSize) {
        if (status == null || maxCount <= 0) {
            return Collections.emptyList();
        }
        List<AgentPlanEntity> result = new ArrayList<>();
        int offset = 0;
        while (result.size() < maxCount) {
            int limit = Math.min(batchSize, maxCount - result.size());
            List<AgentPlanEntity> page = agentPlanRepository.findByStatusPaged(status, offset, limit);
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < limit) {
                break;
            }
            offset += page.size();
        }
        return result;
    }

    private List<AgentPlanEntity> mergePlans(List<AgentPlanEntity> readyPlans,
                                             List<AgentPlanEntity> runningPlans) {
        Map<Long, AgentPlanEntity> planMap = new LinkedHashMap<>();
        appendPlans(planMap, readyPlans);
        appendPlans(planMap, runningPlans);
        if (planMap.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(planMap.values());
    }

    private void appendPlans(Map<Long, AgentPlanEntity> planMap,
                             List<AgentPlanEntity> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        for (AgentPlanEntity plan : plans) {
            if (plan == null || plan.getId() == null || plan.getStatus() == null) {
                continue;
            }
            planMap.putIfAbsent(plan.getId(), plan);
        }
    }

    private void processBatch(List<AgentPlanEntity> plans, SyncStats syncStats) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        List<Long> planIds = new ArrayList<>(plans.size());
        for (AgentPlanEntity plan : plans) {
            if (plan != null && plan.getId() != null) {
                planIds.add(plan.getId());
            }
        }
        if (planIds.isEmpty()) {
            return;
        }

        Map<Long, PlanTaskStatusStat> statMap = new HashMap<>();
        List<PlanTaskStatusStat> stats = agentTaskRepository.summarizeByPlanIds(planIds);
        if (stats != null) {
            for (PlanTaskStatusStat stat : stats) {
                if (stat == null || stat.getPlanId() == null) {
                    continue;
                }
                statMap.put(stat.getPlanId(), stat);
            }
        }

        for (AgentPlanEntity plan : plans) {
            if (plan == null || plan.getId() == null || plan.getStatus() == null) {
                continue;
            }
            syncStats.processedCount++;
            reconcilePlan(plan, statMap.get(plan.getId()), syncStats);
        }
    }

    private void reconcilePlan(AgentPlanEntity plan,
                               PlanTaskStatusStat stat,
                               SyncStats syncStats) {
        PlanTransitionDomainService.PlanAggregateStatus aggregateStatus =
                planTransitionDomainService.resolveAggregateStatus(stat);
        PlanStatusEnum targetStatus = planTransitionDomainService.resolveTargetStatus(plan.getStatus(), aggregateStatus);
        if (targetStatus == null || targetStatus == plan.getStatus()) {
            return;
        }
        PlanStatusEnum beforeStatus = plan.getStatus();

        try {
            planTransitionDomainService.transitPlan(plan, targetStatus);
            agentPlanRepository.update(plan);
            syncStats.advancedCount++;

            if (targetStatus == PlanStatusEnum.COMPLETED || targetStatus == PlanStatusEnum.FAILED) {
                syncStats.finalizeAttemptCount++;
                TurnFinalizeApplicationService.TurnFinalizeResult turnResult =
                        turnFinalizeApplicationService.finalizeByPlan(plan.getId(), targetStatus);
                if (turnResult != null
                        && turnResult.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.SKIPPED_NOT_TERMINAL) {
                    log.info("Plan finalized skipped because turn is not terminal candidate. planId={}, status={}",
                            plan.getId(),
                            targetStatus);
                }
                if (turnResult != null
                        && turnResult.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED) {
                    syncStats.finalizeDedupCount++;
                }

                publishPlanFinishedEvent(plan, turnResult);
                syncStats.finishedPublishCount++;
            }

            log.debug("Plan status advanced by task aggregate. planId={}, from={}, to={}",
                    plan.getId(),
                    beforeStatus,
                    targetStatus);
        } catch (RuntimeException ex) {
            if (isOptimisticLock(ex)) {
                log.debug("Plan status reconcile skipped due to optimistic lock. planId={}, status={}, error={}",
                        plan.getId(),
                        beforeStatus,
                        ex.getMessage());
                return;
            }
            syncStats.errorCount++;
            log.warn("Plan status reconcile failed. planId={}, status={}, target={}, error={}",
                    plan.getId(),
                    beforeStatus,
                    targetStatus,
                    ex.getMessage());
        }
    }

    private boolean isOptimisticLock(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("Optimistic lock");
    }

    private void publishPlanFinishedEvent(AgentPlanEntity plan,
                                          TurnFinalizeApplicationService.TurnFinalizeResult turnResult) {
        if (plan == null || plan.getId() == null) {
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("planId", plan.getId());
            data.put("status", plan.getStatus() == null ? null : plan.getStatus().name());
            if (turnResult != null) {
                data.put("turnId", turnResult.getTurnId());
                data.put("assistantMessageId", turnResult.getAssistantMessageId());
                data.put("assistantSummary", turnResult.getAssistantSummary());
                data.put("turnStatus", turnResult.getTurnStatus() == null ? null : turnResult.getTurnStatus().name());
            }
            planTaskEventPublisher.publish(PlanTaskEventTypeEnum.PLAN_FINISHED,
                    plan.getId(),
                    null,
                    data);
        } catch (Exception ex) {
            log.warn("Failed to publish plan finished event. planId={}, error={}", plan.getId(), ex.getMessage());
        }
    }

    public record SyncResult(int processedCount,
                             int advancedCount,
                             int finalizeAttemptCount,
                             int finalizeDedupCount,
                             int finishedPublishCount,
                             int errorCount) {
        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, 0, 0, 0);
        }
    }

    private static final class SyncStats {
        private int processedCount;
        private int advancedCount;
        private int finalizeAttemptCount;
        private int finalizeDedupCount;
        private int finishedPublishCount;
        private int errorCount;

        private SyncResult toResult() {
            return new SyncResult(processedCount,
                    advancedCount,
                    finalizeAttemptCount,
                    finalizeDedupCount,
                    finishedPublishCount,
                    errorCount);
        }
    }
}
