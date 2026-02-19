package com.getoffer.infrastructure.planning;

import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow 路由决策落库与指标服务。
 */
public class WorkflowRoutingDecisionService {

    private static final String METRIC_PLANNER_ROUTE_TOTAL = "agent.planner.route.total";
    private static final String METRIC_PLANNER_FALLBACK_TOTAL = "agent.planner.fallback.total";

    private final IRoutingDecisionRepository routingDecisionRepository;
    private final MeterRegistry meterRegistry;
    private final PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService;

    public WorkflowRoutingDecisionService(IRoutingDecisionRepository routingDecisionRepository,
                                          MeterRegistry meterRegistry,
                                          PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService) {
        this.routingDecisionRepository = routingDecisionRepository;
        this.meterRegistry = meterRegistry;
        this.plannerFallbackPolicyDomainService = plannerFallbackPolicyDomainService;
    }

    public RoutingDecisionEntity saveAndRecord(Long sessionId,
                                               Map<String, Object> extraContext,
                                               WorkflowRoutingResolveService.RoutedWorkflow routedWorkflow) {
        RoutingDecisionEntity decision = buildRoutingDecisionEntity(sessionId, extraContext, routedWorkflow);
        RoutingDecisionEntity saved = routingDecisionRepository.save(decision);
        recordRoutingMetrics(saved);
        return saved;
    }

    private RoutingDecisionEntity buildRoutingDecisionEntity(Long sessionId,
                                                             Map<String, Object> extraContext,
                                                             WorkflowRoutingResolveService.RoutedWorkflow routedWorkflow) {
        RoutingDecisionEntity decision = new RoutingDecisionEntity();
        decision.setSessionId(sessionId);
        decision.setTurnId(extractTurnId(extraContext));
        decision.setDecisionType(routedWorkflow.decisionType());
        decision.setStrategy(routedWorkflow.strategy());
        decision.setScore(routedWorkflow.score());
        decision.setReason(routedWorkflow.reason());
        if (routedWorkflow.definition() != null) {
            decision.setDefinitionId(routedWorkflow.definition().getId());
            decision.setDefinitionKey(routedWorkflow.definition().getDefinitionKey());
            decision.setDefinitionVersion(routedWorkflow.definition().getVersion());
        }
        if (routedWorkflow.draft() != null) {
            decision.setDraftId(routedWorkflow.draft().getId());
            decision.setDraftKey(routedWorkflow.draft().getDraftKey());
        }
        decision.setSourceType(routedWorkflow.sourceType());
        decision.setFallbackFlag(routedWorkflow.fallbackFlag());
        decision.setFallbackReason(routedWorkflow.fallbackReason());
        decision.setPlannerAttempts(routedWorkflow.plannerAttempts());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", routedWorkflow.sourceType());
        metadata.put("fallbackFlag", routedWorkflow.fallbackFlag());
        metadata.put("fallbackReason", routedWorkflow.fallbackReason());
        metadata.put("plannerAttempts", routedWorkflow.plannerAttempts());
        decision.setMetadata(metadata);
        return decision;
    }

    private void recordRoutingMetrics(RoutingDecisionEntity decision) {
        if (decision == null || meterRegistry == null) {
            return;
        }
        String decisionTypeTag = decision.getDecisionType() == null
                ? "UNKNOWN"
                : decision.getDecisionType().name();
        meterRegistry.counter(METRIC_PLANNER_ROUTE_TOTAL, "decision_type", decisionTypeTag).increment();

        boolean fallback = Boolean.TRUE.equals(decision.getFallbackFlag())
                || decision.getDecisionType() == RoutingDecisionTypeEnum.FALLBACK;
        if (!fallback) {
            return;
        }
        String reasonTag = normalizeFallbackReasonTag(decision.getFallbackReason());
        meterRegistry.counter(METRIC_PLANNER_FALLBACK_TOTAL, "reason", reasonTag).increment();
    }

    private String normalizeFallbackReasonTag(String fallbackReason) {
        return plannerFallbackPolicyDomainService.normalizeFallbackReasonTag(fallbackReason);
    }

    private Long extractTurnId(Map<String, Object> extraContext) {
        if (extraContext == null || extraContext.isEmpty()) {
            return null;
        }
        Object value = extraContext.get("turnId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
