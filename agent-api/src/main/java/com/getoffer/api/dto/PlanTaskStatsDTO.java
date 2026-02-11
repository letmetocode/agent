package com.getoffer.api.dto;

import lombok.Data;

/**
 * 计划任务统计 DTO。
 */
@Data
public class PlanTaskStatsDTO {

    private Long total;
    private Long pending;
    private Long ready;
    private Long runningLike;
    private Long completed;
    private Long failed;
    private Long skipped;
}
