package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行记录 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 任务 ID (关联 agent_tasks.id)
     */
    private Long taskId;

    /**
     * 尝试次数
     */
    private Integer attemptNumber;

    /**
     * Prompt 快照 (包含 System + User + History)
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
     * Token 使用情况 (JSONB)
     */
    private String tokenUsage;

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
     * 创建时间
     */
    private LocalDateTime createdAt;
}
