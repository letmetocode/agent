package com.getoffer.domain.task.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plan 下任务状态聚合统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanTaskStatusStat {

    /**
     * Plan ID
     */
    private Long planId;

    /**
     * 任务总数
     */
    private Long total;

    /**
     * FAILED 数量
     */
    private Long failedCount;

    /**
     * RUNNING/VALIDATING/REFINING 数量
     */
    private Long runningLikeCount;

    /**
     * 终态数量（COMPLETED/FAILED/SKIPPED）
     */
    private Long terminalCount;
}
