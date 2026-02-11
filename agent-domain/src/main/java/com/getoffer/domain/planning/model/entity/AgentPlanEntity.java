package com.getoffer.domain.planning.model.entity;

import com.getoffer.types.enums.PlanStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 执行计划领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentPlanEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 路由决策 ID
     */
    private Long routeDecisionId;

    /**
     * Workflow Definition ID (可空)
     */
    private Long workflowDefinitionId;

    /**
     * Workflow Draft ID (可空)
     */
    private Long workflowDraftId;

    /**
     * 计划目标
     */
    private String planGoal;

    /**
     * 执行图 (解析后的 Map，运行时图谱副本)
     */
    private Map<String, Object> executionGraph;

    /**
     * 定义审计快照 (解析后的 Map，非执行事实)
     */
    private Map<String, Object> definitionSnapshot;

    /**
     * 全局上下文 (解析后的 Map，黑板)
     */
    private Map<String, Object> globalContext;

    /**
     * 状态
     */
    private PlanStatusEnum status;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 错误摘要
     */
    private String errorSummary;

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
     * 验证计划是否有效
     */
    public void validate() {
        if (sessionId == null) {
            throw new IllegalStateException("Session ID cannot be null");
        }
        if (planGoal == null || planGoal.trim().isEmpty()) {
            throw new IllegalStateException("Plan goal cannot be empty");
        }
        if (routeDecisionId == null) {
            throw new IllegalStateException("Route decision id cannot be null");
        }
        if (executionGraph == null || executionGraph.isEmpty()) {
            throw new IllegalStateException("Execution graph cannot be empty");
        }
        if (definitionSnapshot == null || definitionSnapshot.isEmpty()) {
            throw new IllegalStateException("Definition snapshot cannot be empty");
        }
        if (status == null) {
            throw new IllegalStateException("Status cannot be null");
        }
    }

    /**
     * 开始执行
     */
    public void startExecution() {
        if (this.status != PlanStatusEnum.READY) {
            throw new IllegalStateException("Plan must be in READY status to start execution");
        }
        this.status = PlanStatusEnum.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 暂停执行
     */
    public void pause() {
        if (this.status != PlanStatusEnum.RUNNING) {
            throw new IllegalStateException("Only running plans can be paused");
        }
        this.status = PlanStatusEnum.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 恢复执行
     */
    public void resume() {
        if (this.status != PlanStatusEnum.PAUSED) {
            throw new IllegalStateException("Only paused plans can be resumed");
        }
        this.status = PlanStatusEnum.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 完成执行
     */
    public void complete() {
        if (this.status != PlanStatusEnum.RUNNING) {
            throw new IllegalStateException("Only running plans can be completed");
        }
        this.status = PlanStatusEnum.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 从 READY 或 RUNNING 进入完成态。
     */
    public void completeFromReadyOrRunning() {
        if (this.status == PlanStatusEnum.READY) {
            this.status = PlanStatusEnum.COMPLETED;
            this.updatedAt = LocalDateTime.now();
            return;
        }
        if (this.status == PlanStatusEnum.RUNNING) {
            complete();
            return;
        }
        throw new IllegalStateException("Plan must be in READY or RUNNING status to complete");
    }

    /**
     * 标记为失败
     */
    public void fail(String errorSummary) {
        this.status = PlanStatusEnum.FAILED;
        this.errorSummary = errorSummary;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 取消执行
     */
    public void cancel() {
        if (this.status == PlanStatusEnum.COMPLETED || this.status == PlanStatusEnum.FAILED) {
            throw new IllegalStateException("Cannot cancel completed or failed plans");
        }
        this.status = PlanStatusEnum.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 准备执行
     */
    public void ready() {
        if (this.status != PlanStatusEnum.PLANNING) {
            throw new IllegalStateException("Plan must be in PLANNING status to be ready");
        }
        this.status = PlanStatusEnum.READY;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新全局上下文
     */
    public void updateGlobalContext(Map<String, Object> context) {
        this.globalContext = context;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新全局上下文中的单个值
     */
    public void putContextValue(String key, Object value) {
        if (this.globalContext == null) {
            this.globalContext = new java.util.HashMap<>();
        }
        this.globalContext.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 从全局上下文中获取值
     */
    public Object getContextValue(String key) {
        return this.globalContext != null ? this.globalContext.get(key) : null;
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
        return this.status == PlanStatusEnum.READY || this.status == PlanStatusEnum.RUNNING;
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return this.status == PlanStatusEnum.COMPLETED;
    }

    /**
     * 检查是否失败
     */
    public boolean isFailed() {
        return this.status == PlanStatusEnum.FAILED;
    }
}
