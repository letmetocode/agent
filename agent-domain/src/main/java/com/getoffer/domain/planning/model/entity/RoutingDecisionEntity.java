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
}

