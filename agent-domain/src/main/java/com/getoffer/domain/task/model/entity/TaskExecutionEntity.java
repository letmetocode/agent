package com.getoffer.domain.task.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务执行记录领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class TaskExecutionEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 任务 ID
     */
    private Long taskId;

    /**
     * 尝试次数
     */
    private Integer attemptNumber;

    /**
     * Prompt 快照
     */
    private String promptSnapshot;

    /**
     * LLM 原始响应
     */
    private String llmResponseRaw;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * Token 使用情况 (解析后的 Map)
     */
    private Map<String, Object> tokenUsage;

    /**
     * 执行耗时 (毫秒)
     */
    private Long executionTimeMs;

    /**
     * 是否验证通过
     */
    private Boolean isValid;

    /**
     * 验证反馈
     */
    private String validationFeedback;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 错误类型（结构化分类）
     */
    private String errorType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 验证执行记录是否有效
     */
    public void validate() {
        if (taskId == null) {
            throw new IllegalStateException("Task ID cannot be null");
        }
        if (attemptNumber == null || attemptNumber < 1) {
            throw new IllegalStateException("Attempt number must be greater than 0");
        }
    }

    /**
     * 标记为验证通过
     */
    public void markAsValid(String feedback) {
        this.isValid = true;
        this.validationFeedback = feedback;
    }

    /**
     * 标记为验证失败
     */
    public void markAsInvalid(String feedback) {
        this.isValid = false;
        this.validationFeedback = feedback;
    }

    /**
     * 记录执行错误
     */
    public void recordError(String errorMessage) {
        this.errorMessage = errorMessage;
        this.isValid = false;
    }

    /**
     * 设置执行耗时
     */
    public void setExecutionTime(long startTimeMs) {
        this.executionTimeMs = System.currentTimeMillis() - startTimeMs;
    }

    /**
     * 检查是否验证通过
     */
    public boolean isValidated() {
        return Boolean.TRUE.equals(this.isValid);
    }

    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return this.errorMessage != null && !this.errorMessage.trim().isEmpty();
    }

    /**
     * 获取总 Token 使用量
     */
    public Integer getTotalTokens() {
        if (this.tokenUsage == null) {
            return 0;
        }
        Object totalTokens = this.tokenUsage.get("total_tokens");
        if (totalTokens instanceof Integer) {
            return (Integer) totalTokens;
        }
        return 0;
    }
}
