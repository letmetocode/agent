package com.getoffer.domain.planning.model.entity;

import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 路由决策领域实体。
 */
@Data
public class RoutingDecisionEntity {

    private Long id;
    private Long sessionId;
    private Long turnId;
    private RoutingDecisionTypeEnum decisionType;
    private String strategy;
    private BigDecimal score;
    private String reason;
    private Long definitionId;
    private String definitionKey;
    private Integer definitionVersion;
    private Long draftId;
    private String draftKey;
    private String sourceType;
    private Boolean fallbackFlag;
    private String fallbackReason;
    private Integer plannerAttempts;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public void validate() {
        if (sessionId == null) {
            throw new IllegalStateException("Session id cannot be null");
        }
        if (decisionType == null) {
            throw new IllegalStateException("Decision type cannot be null");
        }
    }

    public boolean isFallbackDecision() {
        return decisionType == RoutingDecisionTypeEnum.FALLBACK || Boolean.TRUE.equals(fallbackFlag);
    }

    public void markFallback(String sourceType, String fallbackReason, Integer plannerAttempts) {
        this.decisionType = RoutingDecisionTypeEnum.FALLBACK;
        this.sourceType = sourceType;
        this.fallbackFlag = true;
        this.fallbackReason = fallbackReason;
        this.plannerAttempts = plannerAttempts;
    }

    public void bindDefinitionHit(Long definitionId,
                                  String definitionKey,
                                  Integer definitionVersion,
                                  String strategy,
                                  BigDecimal score,
                                  String reason) {
        this.decisionType = RoutingDecisionTypeEnum.HIT_PRODUCTION;
        this.definitionId = definitionId;
        this.definitionKey = definitionKey;
        this.definitionVersion = definitionVersion;
        this.strategy = strategy;
        this.score = score;
        this.reason = reason;
        this.fallbackFlag = false;
        this.fallbackReason = null;
    }

    public void bindDraftCandidate(Long draftId,
                                   String draftKey,
                                   String strategy,
                                   BigDecimal score,
                                   String reason) {
        this.decisionType = RoutingDecisionTypeEnum.CANDIDATE;
        this.draftId = draftId;
        this.draftKey = draftKey;
        this.strategy = strategy;
        this.score = score;
        this.reason = reason;
    }
}
