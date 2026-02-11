package com.getoffer.domain.planning.model.valobj;

import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 路由结果值对象。
 */
@Data
public class RoutingDecisionResult {

    private RoutingDecisionTypeEnum decisionType;
    private String strategy;
    private BigDecimal score;
    private String reason;

    private Long definitionId;
    private String definitionKey;
    private Integer definitionVersion;

    private Long draftId;
    private String draftKey;

    public boolean isDefinitionHit() {
        return decisionType == RoutingDecisionTypeEnum.HIT_PRODUCTION;
    }

    public boolean isDraftRoute() {
        return decisionType == RoutingDecisionTypeEnum.CANDIDATE
                || decisionType == RoutingDecisionTypeEnum.FALLBACK;
    }
}

