package com.getoffer.test.domain;

import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PlannerFallbackPolicyDomainServiceTest {

    private final PlannerFallbackPolicyDomainService service = new PlannerFallbackPolicyDomainService();

    @Test
    public void shouldRetryWhenRootPlanningErrorIsRetryableAndAttemptsRemain() {
        PlannerFallbackPolicyDomainService.RootPlanningFailureDecision decision =
                service.decideOnRootPlanningFailure(1, 3, false, false);

        Assertions.assertTrue(decision.shouldRetry());
        Assertions.assertEquals(PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNING_FAILED, decision.fallbackReason());
    }

    @Test
    public void shouldStopRetryWhenSoftTimeoutOccurs() {
        PlannerFallbackPolicyDomainService.RootPlanningFailureDecision decision =
                service.decideOnRootPlanningFailure(1, 3, true, true);

        Assertions.assertFalse(decision.shouldRetry());
        Assertions.assertEquals(PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNER_SOFT_TIMEOUT, decision.fallbackReason());
    }

    @Test
    public void shouldResolvePlannerAttemptsBySourceTypeAndFallbackReason() {
        int rootSourceAttempts = service.resolvePlannerAttempts(
                PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_ROOT,
                null,
                null,
                3
        );
        int fallbackAttempts = service.resolvePlannerAttempts(
                PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_FALLBACK,
                PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNING_FAILED,
                null,
                3
        );
        int fallbackRecordedAttempts = service.resolvePlannerAttempts(
                PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_FALLBACK,
                PlannerFallbackPolicyDomainService.FALLBACK_REASON_ROOT_PLANNING_FAILED,
                2,
                3
        );
        int nonFallbackAttempts = service.resolvePlannerAttempts("PRODUCTION_ACTIVE", null, null, 3);

        Assertions.assertEquals(1, rootSourceAttempts);
        Assertions.assertEquals(3, fallbackAttempts);
        Assertions.assertEquals(2, fallbackRecordedAttempts);
        Assertions.assertEquals(0, nonFallbackAttempts);
    }

    @Test
    public void shouldBuildFallbackAgentCandidatesInStableOrder() {
        List<String> candidates = service.buildRootFallbackAgentCandidates("assistant", "root");
        List<String> duplicated = service.buildRootFallbackAgentCandidates("assistant", "assistant");

        Assertions.assertEquals(List.of("assistant", "root"), candidates);
        Assertions.assertEquals(List.of("assistant"), duplicated);
    }

    @Test
    public void shouldNormalizeFallbackReasonTag() {
        Assertions.assertEquals("UNKNOWN", service.normalizeFallbackReasonTag(null));
        Assertions.assertEquals("ROOT_PLANNING_FAILED", service.normalizeFallbackReasonTag("root_planning_failed"));
        Assertions.assertEquals("OTHER", service.normalizeFallbackReasonTag("custom_reason"));
    }
}
