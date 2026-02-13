package com.getoffer.domain.task.model.entity;

import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TaskTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
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
     * 任务类型
     */
    private TaskTypeEnum taskType;

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
     * claim 持有者
     */
    private String claimOwner;

    /**
     * claim 时间
     */
    private LocalDateTime claimAt;

    /**
     * lease 过期时间
     */
    private LocalDateTime leaseUntil;

    /**
     * 执行代际（每次 claim 递增）
     */
    private Integer executionAttempt;

    /**
     * 是否由过期 lease 重领（仅运行时观测字段，不持久化）
     */
    private Boolean leaseReclaimed;

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
        if (taskType == null) {
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
        if (this.status != TaskStatusEnum.VALIDATING && this.status != TaskStatusEnum.REFINING
                && this.status != TaskStatusEnum.RUNNING) {
            throw new IllegalStateException("Task must be in VALIDATING/REFINING/RUNNING status to complete");
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

    public void claim(String owner, int leaseSeconds, Integer nextAttempt, boolean reclaimed) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("Claim owner cannot be empty");
        }
        if (!isClaimable()) {
            throw new IllegalStateException("Task cannot be claimed from status: " + this.status);
        }
        this.claimOwner = owner;
        this.claimAt = LocalDateTime.now();
        this.leaseUntil = this.claimAt.plusSeconds(Math.max(leaseSeconds, 1));
        this.executionAttempt = nextAttempt == null ? 0 : nextAttempt;
        this.leaseReclaimed = reclaimed;
        this.status = TaskStatusEnum.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void renewLease(String owner, Integer attempt, int leaseSeconds) {
        if (!isClaimOwner(owner, attempt)) {
            throw new IllegalStateException("Cannot renew lease for stale claim owner/attempt");
        }
        LocalDateTime now = LocalDateTime.now();
        this.leaseUntil = now.plusSeconds(Math.max(leaseSeconds, 1));
        this.updatedAt = now;
    }

    public void releaseClaim() {
        this.claimOwner = null;
        this.claimAt = null;
        this.leaseUntil = null;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isClaimOwner(String owner, Integer attempt) {
        if (owner == null || attempt == null) {
            return false;
        }
        return owner.equals(this.claimOwner) && attempt.equals(this.executionAttempt);
    }

    public boolean isClaimable() {
        return this.status == TaskStatusEnum.READY || this.status == TaskStatusEnum.REFINING || isLeaseExpiredRunning();
    }

    public boolean isLeaseExpiredRunning() {
        return this.status == TaskStatusEnum.RUNNING && this.leaseUntil != null && this.leaseUntil.isBefore(LocalDateTime.now());
    }

    public boolean hasValidClaim() {
        return this.claimOwner != null && !this.claimOwner.isBlank() && this.executionAttempt != null;
    }

    public void applyCriticFeedback(String feedback) {
        Map<String, Object> nextInputContext = this.inputContext == null
                ? new HashMap<>()
                : new HashMap<>(this.inputContext);
        nextInputContext.put("feedback", feedback);
        nextInputContext.put("criticFeedback", feedback);
        this.inputContext = nextInputContext;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.currentRetry = this.normalizedCurrentRetry() + 1;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean exceedsRetryLimit() {
        if (this.maxRetries == null) {
            return false;
        }
        return this.normalizedCurrentRetry() > Math.max(this.maxRetries, 0);
    }

    public int normalizedCurrentRetry() {
        return this.currentRetry == null ? 0 : Math.max(this.currentRetry, 0);
    }

    public int normalizedMaxRetries() {
        if (this.maxRetries == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(this.maxRetries, 0);
    }

    public boolean hasRetryBudget() {
        return this.normalizedCurrentRetry() < this.normalizedMaxRetries();
    }

    public boolean canTimeoutRetry(int timeoutRetryCount, int timeoutRetryMax) {
        if (timeoutRetryCount < 0) {
            timeoutRetryCount = 0;
        }
        if (timeoutRetryCount >= Math.max(timeoutRetryMax, 0)) {
            return false;
        }
        return hasRetryBudget();
    }

    public void applyTimeoutRetry(String timeoutMessage) {
        this.currentRetry = this.normalizedCurrentRetry() + 1;
        Map<String, Object> nextInputContext = this.inputContext == null
                ? new HashMap<>()
                : new HashMap<>(this.inputContext);
        nextInputContext.put("feedback", timeoutMessage);
        nextInputContext.put("validationFeedback", timeoutMessage);
        this.inputContext = nextInputContext;
        this.updatedAt = LocalDateTime.now();
    }

    public void rollbackToDispatchQueue() {
        if (this.currentRetry != null && this.currentRetry > 0) {
            rollbackToRefining();
        } else {
            rollbackToReady();
        }
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
     * 回滚为可执行就绪态。
     */
    public void rollbackToReady() {
        this.status = TaskStatusEnum.READY;
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
        return hasRetryBudget();
    }

    /**
     * 检查是否为 Worker 任务
     */
    public boolean isWorkerTask() {
        return this.taskType == TaskTypeEnum.WORKER;
    }

    /**
     * 检查是否为 Critic 任务
     */
    public boolean isCriticTask() {
        return this.taskType == TaskTypeEnum.CRITIC;
    }

    /**
     * 检查是否有依赖
     */
    public boolean hasDependencies() {
        return this.dependencyNodeIds != null && !this.dependencyNodeIds.isEmpty();
    }
}
