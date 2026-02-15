package com.getoffer.domain.planning.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Planner 降级策略领域服务：统一 Root 规划重试/降级判定、原因归一与候选 AgentKey 顺序。
 */
@Service
public class PlannerFallbackPolicyDomainService {

    private static final PlannerFallbackPolicyDomainService DEFAULT_INSTANCE = new PlannerFallbackPolicyDomainService();

    public static final String SOURCE_TYPE_AUTO_MISS_ROOT = "AUTO_MISS_ROOT";
    public static final String SOURCE_TYPE_AUTO_MISS_FALLBACK = "AUTO_MISS_FALLBACK";

    public static final String FALLBACK_REASON_ROOT_PLANNING_FAILED = "ROOT_PLANNING_FAILED";
    public static final String FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT = "ROOT_PLANNER_SOFT_TIMEOUT";
    public static final String FALLBACK_REASON_ROOT_PLANNER_DISABLED = "ROOT_PLANNER_DISABLED";
    public static final String FALLBACK_REASON_ROOT_PLANNER_MISSING = "ROOT_PLANNER_MISSING";

    private static final Set<String> METRIC_REASON_TAG_WHITELIST = Set.of(
            SOURCE_TYPE_AUTO_MISS_FALLBACK,
            FALLBACK_REASON_ROOT_PLANNING_FAILED,
            FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT,
            FALLBACK_REASON_ROOT_PLANNER_DISABLED,
            FALLBACK_REASON_ROOT_PLANNER_MISSING,
            "UNKNOWN"
    );

    public RootPlanningFailureDecision decideOnRootPlanningFailure(int attempt,
                                                                   int maxAttempts,
                                                                   boolean nonRetryable,
                                                                   boolean softTimeout) {
        String fallbackReason = softTimeout
                ? FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT
                : FALLBACK_REASON_ROOT_PLANNING_FAILED;
        boolean shouldRetry = !nonRetryable && attempt < Math.max(maxAttempts, 1);
        return new RootPlanningFailureDecision(shouldRetry, fallbackReason);
    }

    public int resolvePlannerAttempts(String sourceType,
                                      String fallbackReason,
                                      Integer recordedAttempts,
                                      int rootMaxAttempts) {
        if (equals(SOURCE_TYPE_AUTO_MISS_ROOT, sourceType)) {
            return 1;
        }
        boolean isRetryExhaustedFallback = equals(SOURCE_TYPE_AUTO_MISS_FALLBACK, sourceType)
                && (equalsIgnoreCase(fallbackReason, FALLBACK_REASON_ROOT_PLANNING_FAILED)
                || equalsIgnoreCase(fallbackReason, FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT));
        if (!isRetryExhaustedFallback) {
            return 0;
        }
        if (recordedAttempts != null && recordedAttempts > 0) {
            return recordedAttempts;
        }
        return Math.max(rootMaxAttempts, 1);
    }

    public List<String> buildRootFallbackAgentCandidates(String fallbackAgentKey, String rootAgentKey) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (isNotBlank(fallbackAgentKey)) {
            candidates.add(fallbackAgentKey);
        }
        if (isNotBlank(rootAgentKey)) {
            candidates.add(rootAgentKey);
        }
        return new ArrayList<>(candidates);
    }

    public String normalizeFallbackReasonTag(String fallbackReason) {
        if (!isNotBlank(fallbackReason)) {
            return "UNKNOWN";
        }
        String normalized = fallbackReason.trim().toUpperCase(Locale.ROOT);
        if (METRIC_REASON_TAG_WHITELIST.contains(normalized)) {
            return normalized;
        }
        return "OTHER";
    }

    public static PlannerFallbackPolicyDomainService defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public record RootPlanningFailureDecision(boolean shouldRetry, String fallbackReason) {
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean equals(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }
}
