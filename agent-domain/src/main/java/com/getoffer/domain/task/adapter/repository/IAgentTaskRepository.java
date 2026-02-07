package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.types.enums.TaskStatusEnum;

import java.util.List;

/**
 * 任务仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentTaskRepository {

    /**
     * 保存任务
     */
    AgentTaskEntity save(AgentTaskEntity entity);

    /**
     * 更新任务 (带乐观锁)
     */
    AgentTaskEntity update(AgentTaskEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentTaskEntity findById(Long id);

    /**
     * 根据计划 ID 查询
     */
    List<AgentTaskEntity> findByPlanId(Long planId);

    /**
     * 根据计划 ID 和状态查询
     */
    List<AgentTaskEntity> findByPlanIdAndStatus(Long planId, TaskStatusEnum status);

    /**
     * 根据计划 ID 和节点 ID 查询
     */
    AgentTaskEntity findByPlanIdAndNodeId(Long planId, String nodeId);

    /**
     * 查询所有任务
     */
    List<AgentTaskEntity> findAll();

    /**
     * 根据状态查询
     */
    List<AgentTaskEntity> findByStatus(TaskStatusEnum status);

    /**
     * 查询就绪的任务 (用于调度器)
     */
    List<AgentTaskEntity> findReadyTasks();

    /**
     * 原子 claim 可执行任务（READY/REFINING/过期RUNNING -> RUNNING）。
     */
    List<AgentTaskEntity> claimExecutableTasks(String claimOwner, int limit, int leaseSeconds);

    /**
     * 续约 claim lease。
     */
    boolean renewClaimLease(Long taskId, String claimOwner, Integer executionAttempt, int leaseSeconds);

    /**
     * 按 claim_owner + execution_attempt 条件更新任务终态，防止旧执行者回写污染。
     */
    boolean updateClaimedTaskState(AgentTaskEntity entity);

    /**
     * 查询过期 RUNNING 任务数量（用于监控/报警）。
     */
    long countExpiredRunningTasks();

    /**
     * 批量保存任务
     */
    List<AgentTaskEntity> batchSave(List<AgentTaskEntity> entities);

    /**
     * 批量更新状态
     */
    boolean batchUpdateStatus(Long planId, TaskStatusEnum fromStatus, TaskStatusEnum toStatus);

    /**
     * 按 Plan IDs 聚合任务状态统计。
     */
    List<PlanTaskStatusStat> summarizeByPlanIds(List<Long> planIds);
}
