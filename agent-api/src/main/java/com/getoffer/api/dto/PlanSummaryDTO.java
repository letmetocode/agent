package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划摘要 DTO。
 */
@Data
public class PlanSummaryDTO {

    private Long planId;
    private Long sessionId;
    private String planGoal;
    private String status;
    private Integer priority;
    private String errorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
