package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentTaskPO;
import com.getoffer.infrastructure.dao.po.PlanTaskStatusStatPO;
import com.getoffer.types.enums.TaskStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentTaskDao {

    /**
     * 插入任务
     */
    int insert(AgentTaskPO po);

    /**
     * 根据 ID 更新 (带乐观锁)
     */
    int updateWithVersion(AgentTaskPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    AgentTaskPO selectById(@Param("id") Long id);

    /**
     * 根据计划 ID 查询
     */
    List<AgentTaskPO> selectByPlanId(@Param("planId") Long planId);

    /**
     * 根据计划 ID 和状态查询
     */
    List<AgentTaskPO> selectByPlanIdAndStatus(@Param("planId") Long planId,
                                               @Param("status") TaskStatusEnum status);

    /**
     * 根据计划 ID 和节点 ID 查询
     */
    AgentTaskPO selectByPlanIdAndNodeId(@Param("planId") Long planId,
                                         @Param("nodeId") String nodeId);

    /**
     * 查询所有任务
     */
    List<AgentTaskPO> selectAll();

    /**
     * 根据状态查询
     */
    List<AgentTaskPO> selectByStatus(@Param("status") TaskStatusEnum status);

    /**
     * 查询就绪的任务 (用于调度器)
     */
    List<AgentTaskPO> selectReadyTasks();

    /**
     * 原子 claim 可执行任务。
     */
    List<AgentTaskPO> claimExecutableTasks(@Param("claimOwner") String claimOwner,
                                           @Param("limit") Integer limit,
                                           @Param("leaseSeconds") Integer leaseSeconds);

    /**
     * 原子 claim READY + 过期 RUNNING（READY 优先路径）。
     */
    List<AgentTaskPO> claimReadyLikeTasks(@Param("claimOwner") String claimOwner,
                                          @Param("limit") Integer limit,
                                          @Param("leaseSeconds") Integer leaseSeconds);

    /**
     * 原子 claim REFINING。
     */
    List<AgentTaskPO> claimRefiningTasks(@Param("claimOwner") String claimOwner,
                                         @Param("limit") Integer limit,
                                         @Param("leaseSeconds") Integer leaseSeconds);

    /**
     * 续约 claim lease。
     */
    int renewClaimLease(@Param("id") Long id,
                        @Param("claimOwner") String claimOwner,
                        @Param("executionAttempt") Integer executionAttempt,
                        @Param("leaseSeconds") Integer leaseSeconds);

    /**
     * 按 claim_owner + execution_attempt 条件更新任务状态。
     */
    int updateClaimedTaskState(AgentTaskPO po);

    /**
     * 查询过期 RUNNING 数量。
     */
    Long countExpiredRunningTasks();

    /**
     * 批量插入任务
     */
    int batchInsert(@Param("list") List<AgentTaskPO> list);

    /**
     * 批量更新状态
     */
    int batchUpdateStatus(@Param("planId") Long planId,
                          @Param("fromStatus") TaskStatusEnum fromStatus,
                          @Param("toStatus") TaskStatusEnum toStatus);

    /**
     * 按 Plan IDs 聚合任务状态统计。
     */
    List<PlanTaskStatusStatPO> selectPlanStatusStats(@Param("planIds") List<Long> planIds);
}
