package com.getoffer.domain.task.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 质量实验分桶聚合统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityExperimentSummary {

    /**
     * 实验标识。
     */
    private String experimentKey;

    /**
     * 实验分桶（A/B/CONTROL/...）。
     */
    private String experimentVariant;

    /**
     * 样本总数。
     */
    private Long totalCount;

    /**
     * 通过样本数。
     */
    private Long passCount;

    /**
     * 平均分。
     */
    private Double avgScore;

    /**
     * 最近评估时间。
     */
    private LocalDateTime lastEvaluatedAt;
}
