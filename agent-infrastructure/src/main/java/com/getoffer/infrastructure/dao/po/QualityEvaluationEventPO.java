package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 质量评估事件 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityEvaluationEventPO {

    private Long id;
    private Long planId;
    private Long taskId;
    private Long executionId;
    private String evaluatorType;
    private String experimentKey;
    private String experimentVariant;
    private String schemaVersion;
    private Double score;
    private Boolean pass;
    private String feedback;
    private String payload;
    private LocalDateTime createdAt;
}
