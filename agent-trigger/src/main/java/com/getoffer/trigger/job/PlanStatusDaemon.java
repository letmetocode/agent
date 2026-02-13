package com.getoffer.trigger.job;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 状态推进守护进程：由 Task 聚合状态驱动 Plan 状态闭环。
 */
@Slf4j
@Component
public class PlanStatusDaemon {

    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final TurnFinalizeApplicationService turnResultService;
    private final PlanTransitionDomainService planTransitionDomainService;

    private final int batchSize;
    private final int maxPlansPerRound;
    private final Counter finalizeAttemptCounter;
    private final Counter finalizeDedupCounter;
    private final Counter finishedPublishCounter;

    public PlanStatusDaemon(IAgentPlanRepository agentPlanRepository,
                            IAgentTaskRepository agentTaskRepository,
                            PlanTaskEventPublisher planTaskEventPublisher,
                            TurnFinalizeApplicationService turnResultService,
                            PlanTransitionDomainService planTransitionDomainService,
                            @Value("${plan-status.batch-size:200}") int batchSize,
                            @Value("${plan-status.max-plans-per-round:1000}") int maxPlansPerRound) {
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.turnResultService = turnResultService;
        this.planTransitionDomainService = planTransitionDomainService;
        this.batchSize = batchSize > 0 ? batchSize : 200;
        this.maxPlansPerRound = maxPlansPerRound > 0 ? maxPlansPerRound : 1000;
        this.finalizeAttemptCounter = Counter.builder("agent.plan.finalize.attempt.total").register(Metrics.globalRegistry);
        this.finalizeDedupCounter = Counter.builder("agent.plan.finalize.dedup.total").register(Metrics.globalRegistry);
        this.finishedPublishCounter = Counter.builder("agent.plan.finished.publish.total").register(Metrics.globalRegistry);
    }

    @Scheduled(fixedDelayString = "${plan-status.poll-interval-ms:1000}", scheduler = "daemonScheduler")
    public void syncPlanStatuses() {
        List<AgentPlanEntity> readyPlans = loadPlansByStatus(PlanStatusEnum.READY, maxPlansPerRound);
        int remaining = Math.max(0, maxPlansPerRound - readyPlans.size());
        List<AgentPlanEntity> runningPlans = remaining > 0
                ? loadPlansByStatus(PlanStatusEnum.RUNNING, remaining)
                : Collections.emptyList();
        List<AgentPlanEntity> plans = mergePlans(readyPlans, runningPlans);
        if (plans.isEmpty()) {
            return;
        }

        for (int i = 0; i < plans.size(); i += batchSize) {
            int end = Math.min(i + batchSize, plans.size());
            List<AgentPlanEntity> batch = plans.subList(i, end);
            processBatch(batch);
        }
    }

    private List<AgentPlanEntity> loadPlansByStatus(PlanStatusEnum status, int maxCount) {
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

    private List<AgentPlanEntity> mergePlans(List<AgentPlanEntity> readyPlans, List<AgentPlanEntity> runningPlans) {
        Map<Long, AgentPlanEntity> planMap = new LinkedHashMap<>();
        appendPlans(planMap, readyPlans);
        appendPlans(planMap, runningPlans);
        if (planMap.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(planMap.values());
    }

    private void appendPlans(Map<Long, AgentPlanEntity> planMap, List<AgentPlanEntity> plans) {
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

    private void processBatch(List<AgentPlanEntity> plans) {
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
            reconcilePlan(plan, statMap.get(plan.getId()));
        }
    }

    private void reconcilePlan(AgentPlanEntity plan, PlanTaskStatusStat stat) {
        PlanTransitionDomainService.PlanAggregateStatus aggregateStatus = planTransitionDomainService.resolveAggregateStatus(stat);
        PlanStatusEnum targetStatus = planTransitionDomainService.resolveTargetStatus(plan.getStatus(), aggregateStatus);
        if (targetStatus == null || targetStatus == plan.getStatus()) {
            return;
        }
        PlanStatusEnum beforeStatus = plan.getStatus();

        try {
            planTransitionDomainService.transitPlan(plan, targetStatus);
            agentPlanRepository.update(plan);
            if (targetStatus == PlanStatusEnum.COMPLETED || targetStatus == PlanStatusEnum.FAILED) {
                finalizeAttemptCounter.increment();
                TurnFinalizeApplicationService.TurnFinalizeResult turnResult = turnResultService.finalizeByPlan(plan.getId(), targetStatus);
                if (turnResult != null && turnResult.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.SKIPPED_NOT_TERMINAL) {
                    log.info("Plan finalized skipped because turn is not terminal candidate. planId={}, status={}", plan.getId(), targetStatus);
                }
                if (turnResult != null && turnResult.getOutcome() == TurnFinalizeApplicationService.FinalizeOutcome.ALREADY_FINALIZED) {
                    finalizeDedupCounter.increment();
                }
                publishPlanFinishedEvent(plan, turnResult);
                finishedPublishCounter.increment();
            }
            log.debug("Plan status advanced by task aggregate. planId={}, from={}, to={}",
                    plan.getId(), beforeStatus, targetStatus);
        } catch (RuntimeException ex) {
            if (isOptimisticLock(ex)) {
                log.debug("Plan status reconcile skipped due to optimistic lock. planId={}, status={}, error={}",
                        plan.getId(), beforeStatus, ex.getMessage());
                return;
            }
            log.warn("Plan status reconcile failed. planId={}, status={}, target={}, error={}",
                    plan.getId(), beforeStatus, targetStatus, ex.getMessage());
        }
    }

    private boolean isOptimisticLock(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("Optimistic lock");
    }

    private void publishPlanFinishedEvent(AgentPlanEntity plan, TurnFinalizeApplicationService.TurnFinalizeResult turnResult) {
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
            planTaskEventPublisher.publish(PlanTaskEventTypeEnum.PLAN_FINISHED, plan.getId(), null, data);
        } catch (Exception ex) {
            log.warn("Failed to publish plan finished event. planId={}, error={}", plan.getId(), ex.getMessage());
        }
    }
}
