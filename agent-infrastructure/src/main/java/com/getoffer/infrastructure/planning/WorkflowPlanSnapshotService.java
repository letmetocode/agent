package com.getoffer.infrastructure.planning;

import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan 快照构建服务。
 */
public class WorkflowPlanSnapshotService {

    private final JsonCodec jsonCodec;

    public WorkflowPlanSnapshotService(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public Map<String, Object> buildDefinitionSnapshot(RoutingDecisionTypeEnum decisionType,
                                                       String reason,
                                                       String strategy,
                                                       BigDecimal score,
                                                       WorkflowDefinitionEntity definition,
                                                       WorkflowDraftEntity draft,
                                                       String sourceType,
                                                       Boolean fallbackFlag,
                                                       String fallbackReason,
                                                       Integer plannerAttempts,
                                                       Map<String, Object> toolPolicy,
                                                       Map<String, Object> graphDefinition) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("routeType", decisionType == null ? null : decisionType.name());
        snapshot.put("routeReason", reason);
        snapshot.put("routeStrategy", strategy);
        snapshot.put("routeScore", score);
        if (definition != null) {
            snapshot.put("definitionId", definition.getId());
            snapshot.put("definitionKey", definition.getDefinitionKey());
            snapshot.put("definitionVersion", definition.getVersion());
        }
        if (draft != null) {
            snapshot.put("draftId", draft.getId());
            snapshot.put("draftKey", draft.getDraftKey());
            snapshot.put("draftSourceType", draft.getSourceType());
        }
        snapshot.put("sourceType", sourceType);
        snapshot.put("fallbackFlag", fallbackFlag);
        snapshot.put("fallbackReason", fallbackReason);
        snapshot.put("plannerAttempts", plannerAttempts);
        if (toolPolicy != null && !toolPolicy.isEmpty()) {
            snapshot.put("toolPolicy", deepCopyMap(toolPolicy));
        }
        snapshot.put("executionGraphHash", hashGraph(graphDefinition));
        return snapshot;
    }

    private String hashGraph(Map<String, Object> graphDefinition) {
        return com.getoffer.domain.planning.service.WorkflowGraphPolicyKernel.hashGraph(
                graphDefinition,
                jsonCodec.getObjectMapper()
        );
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
    }
}
