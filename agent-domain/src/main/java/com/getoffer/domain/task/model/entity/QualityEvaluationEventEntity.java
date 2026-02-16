package com.getoffer.domain.task.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 质量评估事件实体：沉淀验证/批评结果，支撑质量分析与 A/B 对比。
 */
@Data
public class QualityEvaluationEventEntity {

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
    private Map<String, Object> payload;
    private LocalDateTime createdAt;

    public void validate() {
        if (planId == null) {
            throw new IllegalStateException("Plan ID cannot be null");
        }
        if (taskId == null) {
            throw new IllegalStateException("Task ID cannot be null");
        }
        if (isBlank(evaluatorType)) {
            throw new IllegalStateException("Evaluator type cannot be blank");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
