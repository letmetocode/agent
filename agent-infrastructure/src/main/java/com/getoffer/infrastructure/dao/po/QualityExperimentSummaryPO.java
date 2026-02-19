package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 质量实验分桶聚合统计 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityExperimentSummaryPO {

    private String experimentKey;
    private String experimentVariant;
    private Long totalCount;
    private Long passCount;
    private Double avgScore;
    private LocalDateTime lastEvaluatedAt;
}
