package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务执行记录详情 DTO。
 */
@Data
public class TaskExecutionDetailDTO {

    private Long executionId;
    private Long taskId;
    private Integer attemptNumber;
    private String promptSnapshot;
    private String llmResponseRaw;
    private String modelName;
    private Map<String, Object> tokenUsage;
    private Long executionTimeMs;
    private Boolean valid;
    private String validationFeedback;
    private String errorMessage;
    private String errorType;
    private LocalDateTime createdAt;
}
