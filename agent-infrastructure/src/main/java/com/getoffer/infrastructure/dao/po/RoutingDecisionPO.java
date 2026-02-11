package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 路由决策 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingDecisionPO {

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
    private String metadata;
    private LocalDateTime createdAt;
}

