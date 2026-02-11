package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 计划详情 DTO。
 */
@Data
public class PlanDetailDTO {

    private Long planId;
    private Long sessionId;
    private Long routeDecisionId;
    private Long workflowDefinitionId;
    private Long workflowDraftId;
    private String planGoal;
    private Map<String, Object> executionGraph;
    private Map<String, Object> definitionSnapshot;
    private Map<String, Object> globalContext;
    private String status;
    private Integer priority;
    private String errorSummary;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
