package com.getoffer.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 会话首屏聚合 DTO。
 */
@Data
public class SessionOverviewDTO {

    private SessionDetailDTO session;
    private List<PlanSummaryDTO> plans;
    private Long latestPlanId;
    private PlanTaskStatsDTO latestPlanTaskStats;
    private List<TaskDetailDTO> latestPlanTasks;
}
