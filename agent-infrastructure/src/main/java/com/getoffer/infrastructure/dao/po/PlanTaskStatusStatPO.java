package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plan 任务状态聚合统计 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanTaskStatusStatPO {

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
