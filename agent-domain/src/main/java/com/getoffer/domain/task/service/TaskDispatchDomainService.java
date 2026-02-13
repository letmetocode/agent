package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Task 调度决策领域服务：负责 claim 限额、READY/REFINING 配额与兜底顺序策略。
 */
@Service
public class TaskDispatchDomainService {

    public int resolveClaimLimit(int claimBatchSize, int claimMaxPerTick, int availablePermits) {
        int perTickLimit = Math.max(Math.min(claimBatchSize, claimMaxPerTick), 0);
        if (perTickLimit <= 0) {
            return 0;
        }
        return Math.min(perTickLimit, Math.max(availablePermits, 0));
    }

    public ClaimPlan planClaim(int claimLimit,
                               boolean claimReadyFirst,
                               double refiningMaxRatio,
                               int refiningMinPerTick) {
        int normalizedLimit = Math.max(claimLimit, 0);
        if (normalizedLimit <= 0) {
            return ClaimPlan.empty();
        }

        int refiningQuota = resolveRefiningQuota(normalizedLimit, refiningMaxRatio, refiningMinPerTick);
        int readyQuota = Math.max(normalizedLimit - refiningQuota, 0);

        List<ClaimSlot> primarySlots = new ArrayList<>(2);
        if (claimReadyFirst) {
            primarySlots.add(new ClaimSlot(true, readyQuota, false));
            primarySlots.add(new ClaimSlot(false, refiningQuota, false));
        } else {
            primarySlots.add(new ClaimSlot(false, refiningQuota, false));
            primarySlots.add(new ClaimSlot(true, readyQuota, false));
        }

        List<Boolean> fallbackOrder = claimReadyFirst
                ? List.of(true, false)
                : List.of(false, true);

        return new ClaimPlan(normalizedLimit, primarySlots, fallbackOrder);
    }

    public boolean hasValidClaim(AgentTaskEntity task) {
        return task != null && task.hasValidClaim();
    }

    private int resolveRefiningQuota(int claimLimit, double refiningMaxRatio, int refiningMinPerTick) {
        int normalizedLimit = Math.max(claimLimit, 0);
        if (normalizedLimit <= 0) {
            return 0;
        }

        double normalizedRatio;
        if (Double.isNaN(refiningMaxRatio) || Double.isInfinite(refiningMaxRatio)) {
            normalizedRatio = 0.3D;
        } else {
            normalizedRatio = Math.max(0D, Math.min(refiningMaxRatio, 1D));
        }

        int ratioQuota = (int) Math.floor(normalizedLimit * normalizedRatio);
        int minQuota = Math.min(Math.max(refiningMinPerTick, 0), normalizedLimit);
        int quota = Math.max(ratioQuota, minQuota);
        return Math.min(Math.max(quota, 0), normalizedLimit);
    }

    public record ClaimSlot(boolean readyLike, int limit, boolean fallback) {
    }

    public record ClaimPlan(int claimLimit,
                            List<ClaimSlot> primarySlots,
                            List<Boolean> fallbackOrder) {

        public static ClaimPlan empty() {
            return new ClaimPlan(0, Collections.emptyList(), Collections.emptyList());
        }
    }
}
