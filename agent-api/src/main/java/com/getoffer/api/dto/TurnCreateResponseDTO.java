package com.getoffer.api.dto;

import lombok.Data;

/**
 * 回合创建响应 DTO（V2）。
 */
@Data
public class TurnCreateResponseDTO {

    private Long sessionId;
    private Long turnId;
    private Long planId;
    private String planGoal;
    private String turnStatus;
    private String assistantMessage;
    private RoutingDecisionDTO routingDecision;
}
