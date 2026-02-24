package com.getoffer.infrastructure.planning;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.model.valobj.RoutingDecisionResult;
import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import com.getoffer.domain.planning.service.WorkflowRoutingPolicyDomainService;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 路由解析服务：负责命中生产定义或回退候选 Draft，并构建路由快照。
 */
public class WorkflowRoutingResolveService {

    private static final String SOURCE_TYPE_AUTO_MISS_FALLBACK =
            PlannerFallbackPolicyDomainService.SOURCE_TYPE_AUTO_MISS_FALLBACK;

    private final IWorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowDraftLifecycleService workflowDraftLifecycleService;
    private final WorkflowRoutingPolicyDomainService workflowRoutingPolicyDomainService;
    private final PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService;
    private final JsonCodec jsonCodec;
    private final int rootMaxAttempts;

    public WorkflowRoutingResolveService(IWorkflowDefinitionRepository workflowDefinitionRepository,
                                         WorkflowDraftLifecycleService workflowDraftLifecycleService,
                                         WorkflowRoutingPolicyDomainService workflowRoutingPolicyDomainService,
                                         PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService,
                                         JsonCodec jsonCodec,
                                         int rootMaxAttempts) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowDraftLifecycleService = workflowDraftLifecycleService;
        this.workflowRoutingPolicyDomainService = workflowRoutingPolicyDomainService;
        this.plannerFallbackPolicyDomainService = plannerFallbackPolicyDomainService;
        this.jsonCodec = jsonCodec;
        this.rootMaxAttempts = Math.max(rootMaxAttempts, 1);
    }

    public RoutingDecisionResult route(String userQuery) {
        WorkflowDefinitionEntity definition = matchDefinition(userQuery, workflowDefinitionRepository.findProductionActive());
        RoutingDecisionResult result = new RoutingDecisionResult();
        result.setStrategy("TRIGGER_TOKEN_SCORE");
        if (definition != null) {
            result.setDecisionType(RoutingDecisionTypeEnum.HIT_PRODUCTION);
            result.setReason("PRODUCTION_DEFINITION_MATCHED");
            result.setScore(BigDecimal.ONE);
            result.setDefinitionId(definition.getId());
            result.setDefinitionKey(definition.getDefinitionKey());
            result.setDefinitionVersion(definition.getVersion());
        } else {
            result.setDecisionType(RoutingDecisionTypeEnum.CANDIDATE);
            result.setReason("PRODUCTION_DEFINITION_MISSED");
            result.setScore(BigDecimal.ZERO);
        }
        return result;
    }

    public RoutedWorkflow resolve(Long sessionId,
                                  String userQuery,
                                  Map<String, Object> extraContext) {
        WorkflowDefinitionEntity definition = matchDefinition(userQuery, workflowDefinitionRepository.findProductionActive());
        if (definition != null) {
            return new RoutedWorkflow(
                    RoutingDecisionTypeEnum.HIT_PRODUCTION,
                    "PRODUCTION_DEFINITION_MATCHED",
                    "TRIGGER_TOKEN_SCORE",
                    BigDecimal.ONE,
                    definition,
                    null,
                    "PRODUCTION_ACTIVE",
                    false,
                    null,
                    0,
                    deepCopyMap(definition.getGraphDefinition()),
                    deepCopyMap(definition.getInputSchema()),
                    deepCopyMap(definition.getDefaultConfig()),
                    deepCopyMap(definition.getToolPolicy())
            );
        }
        return resolveCandidate(sessionId, userQuery, extraContext);
    }

    public RoutedWorkflow resolveCandidate(Long sessionId,
                                           String userQuery,
                                           Map<String, Object> extraContext) {
        WorkflowDraftEntity draft = workflowDraftLifecycleService.loadOrCreateDraft(sessionId, userQuery, extraContext);
        return buildCandidateRoutedWorkflow(draft);
    }

    private RoutedWorkflow buildCandidateRoutedWorkflow(WorkflowDraftEntity draft) {
        RoutingDecisionTypeEnum decisionType = StringUtils.equals(SOURCE_TYPE_AUTO_MISS_FALLBACK, draft.getSourceType())
                ? RoutingDecisionTypeEnum.FALLBACK
                : RoutingDecisionTypeEnum.CANDIDATE;
        String fallbackReason = extractFallbackReason(draft);
        Integer plannerAttempts = resolvePlannerAttempts(draft, fallbackReason);
        return new RoutedWorkflow(
                decisionType,
                StringUtils.defaultIfBlank(draft.getSourceType(), "PRODUCTION_DEFINITION_MISSED"),
                "ROOT_DRAFT_OR_FALLBACK",
                BigDecimal.ZERO,
                null,
                draft,
                draft.getSourceType(),
                decisionType == RoutingDecisionTypeEnum.FALLBACK,
                fallbackReason,
                plannerAttempts,
                deepCopyMap(draft.getGraphDefinition()),
                deepCopyMap(draft.getInputSchema()),
                deepCopyMap(draft.getDefaultConfig()),
                deepCopyMap(draft.getToolPolicy())
        );
    }

    private WorkflowDefinitionEntity matchDefinition(String userQuery, List<WorkflowDefinitionEntity> definitions) {
        return workflowRoutingPolicyDomainService.matchDefinition(userQuery, definitions);
    }

    private String extractFallbackReason(WorkflowDraftEntity draft) {
        if (draft == null || draft.getConstraints() == null) {
            return null;
        }
        Object fallbackReason = draft.getConstraints().get("fallbackReason");
        if (fallbackReason == null) {
            return null;
        }
        String text = String.valueOf(fallbackReason).trim();
        return StringUtils.isBlank(text) ? null : text;
    }

    private Integer resolvePlannerAttempts(WorkflowDraftEntity draft, String fallbackReason) {
        if (draft == null) {
            return 0;
        }
        Integer attempts = getInteger(draft.getConstraints(), "rootPlanningAttempts", "plannerAttempts");
        return plannerFallbackPolicyDomainService.resolvePlannerAttempts(
                draft.getSourceType(),
                fallbackReason,
                attempts,
                rootMaxAttempts
        );
    }

    private Integer getInteger(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
    }

    public record RoutedWorkflow(RoutingDecisionTypeEnum decisionType,
                                 String reason,
                                 String strategy,
                                 BigDecimal score,
                                 WorkflowDefinitionEntity definition,
                                 WorkflowDraftEntity draft,
                                 String sourceType,
                                 Boolean fallbackFlag,
                                 String fallbackReason,
                                 Integer plannerAttempts,
                                 Map<String, Object> graphDefinition,
                                 Map<String, Object> inputSchema,
                                 Map<String, Object> defaultConfig,
                                 Map<String, Object> toolPolicy) {
    }
}
