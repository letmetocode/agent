package com.getoffer.domain.task.model.entity;

import com.getoffer.types.enums.TaskStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentTaskEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 计划 ID
     */
    private Long planId;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务类型 ('WORKER', 'CRITIC')
     */
    private String taskType;

    /**
     * 状态
     */
    private TaskStatusEnum status;

    /**
     * DAG 依赖节点 IDs (解析后的 List)
     */
    private List<String> dependencyNodeIds;

    /**
     * 输入上下文 (解析后的 Map)
     */
    private Map<String, Object> inputContext;

    /**
     * 配置快照 (解析后的 Map)
     */
    private Map<String, Object> configSnapshot;

    /**
     * 输出结果
     */
    private String outputResult;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 当前重试次数
     */
    private Integer currentRetry;

    /**
     * 版本号 (乐观锁)
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 验证任务是否有效
     */
    public void validate() {
        if (planId == null) {
            throw new IllegalStateException("Plan ID cannot be null");
        }
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalStateException("Node ID cannot be empty");
        }
        if (taskType == null || taskType.trim().isEmpty()) {
            throw new IllegalStateException("Task type cannot be empty");
        }
        if (status == null) {
            throw new IllegalStateException("Status cannot be null");
        }
        if (dependencyNodeIds == null) {
            throw new IllegalStateException("Dependency node IDs cannot be null");
        }
        if (configSnapshot == null) {
            throw new IllegalStateException("Config snapshot cannot be null");
        }
    }

    /**
     * 标记为就绪
     */
    public void markReady() {
        if (this.status != TaskStatusEnum.PENDING) {
            throw new IllegalStateException("Task must be in PENDING status to be marked ready");
        }
        this.status = TaskStatusEnum.READY;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 开始执行
     */
    public void start() {
        if (this.status != TaskStatusEnum.READY && this.status != TaskStatusEnum.REFINING) {
            throw new IllegalStateException("Task must be in READY or REFINING status to start");
        }
        this.status = TaskStatusEnum.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 开始验证
     */
    public void startValidation() {
        if (this.status != TaskStatusEnum.RUNNING) {
            throw new IllegalStateException("Task must be in RUNNING status to start validation");
        }
        this.status = TaskStatusEnum.VALIDATING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 开始优化 (根据反馈重新执行)
     */
    public void startRefining() {
        if (this.status != TaskStatusEnum.VALIDATING) {
            throw new IllegalStateException("Task must be in VALIDATING status to start refining");
        }
        if (this.currentRetry >= this.maxRetries) {
            throw new IllegalStateException("Max retries exceeded");
        }
        this.status = TaskStatusEnum.REFINING;
        this.currentRetry++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 完成任务
     */
    public void complete(String outputResult) {
        if (this.status != TaskStatusEnum.VALIDATING && this.status != TaskStatusEnum.REFINING) {
            throw new IllegalStateException("Task must be in VALIDATING or REFINING status to complete");
        }
        this.status = TaskStatusEnum.COMPLETED;
        this.outputResult = outputResult;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 失败任务
     */
    public void fail(String errorMessage) {
        this.status = TaskStatusEnum.FAILED;
        this.outputResult = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 跳过任务
     */
    public void skip() {
        if (this.status != TaskStatusEnum.PENDING) {
            throw new IllegalStateException("Only PENDING tasks can be skipped");
        }
        this.status = TaskStatusEnum.SKIPPED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 回滚为待执行。
     */
    public void resetToPending() {
        this.status = TaskStatusEnum.PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 回滚为需要重试。
     */
    public void rollbackToRefining() {
        this.status = TaskStatusEnum.REFINING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新输出结果
     */
    public void updateOutput(String output) {
        this.outputResult = output;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新输入上下文
     */
    public void updateInputContext(Map<String, Object> inputContext) {
        this.inputContext = inputContext;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 增加版本号 (用于乐观锁)
     */
    public void incrementVersion() {
        this.version++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否可以执行
     */
    public boolean isExecutable() {
        return this.status == TaskStatusEnum.READY;
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return this.status == TaskStatusEnum.COMPLETED;
    }

    /**
     * 检查是否失败
     */
    public boolean isFailed() {
        return this.status == TaskStatusEnum.FAILED;
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return this.currentRetry < this.maxRetries;
    }

    /**
     * 检查是否为 Worker 任务
     */
    public boolean isWorkerTask() {
        return "WORKER".equalsIgnoreCase(this.taskType);
    }

    /**
     * 检查是否为 Critic 任务
     */
    public boolean isCriticTask() {
        return "CRITIC".equalsIgnoreCase(this.taskType);
    }

    /**
     * 检查是否有依赖
     */
    public boolean hasDependencies() {
        return this.dependencyNodeIds != null && !this.dependencyNodeIds.isEmpty();
    }
}
