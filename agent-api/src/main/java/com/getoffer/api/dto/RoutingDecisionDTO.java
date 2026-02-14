package com.getoffer.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 路由决策 DTO（V3 会话编排返回结构）。
 */
@Data
public class RoutingDecisionDTO {

    private Long routingDecisionId;
    private Long sessionId;
    private Long turnId;
    private String decisionType;
    private String strategy;
    private BigDecimal score;
    private String reason;
    private String sourceType;
    private Boolean fallbackFlag;
    private String fallbackReason;
    private Integer plannerAttempts;
    private Long definitionId;
    private String definitionKey;
    private Integer definitionVersion;
    private Long draftId;
    private String draftKey;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
