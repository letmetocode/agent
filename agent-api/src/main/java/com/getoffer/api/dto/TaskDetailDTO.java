package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务详情 DTO。
 */
@Data
public class TaskDetailDTO {

    private Long taskId;
    private Long planId;
    private String nodeId;
    private String name;
    private String taskType;
    private String status;
    private List<String> dependencyNodeIds;
    private Map<String, Object> inputContext;
    private Map<String, Object> configSnapshot;
    private String outputResult;
    private Integer maxRetries;
    private Integer currentRetry;
    private String claimOwner;
    private LocalDateTime claimAt;
    private LocalDateTime leaseUntil;
    private Integer executionAttempt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
