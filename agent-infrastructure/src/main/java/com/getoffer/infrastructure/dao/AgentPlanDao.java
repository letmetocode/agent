package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentPlanPO;
import com.getoffer.types.enums.PlanStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 执行计划 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentPlanDao {

    /**
     * 插入执行计划
     */
    int insert(AgentPlanPO po);

    /**
     * 根据 ID 更新 (带乐观锁)
     */
    int updateWithVersion(AgentPlanPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    AgentPlanPO selectById(@Param("id") Long id);

    /**
     * 根据会话 ID 查询
     */
    List<AgentPlanPO> selectBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 根据状态查询
     */
    List<AgentPlanPO> selectByStatus(@Param("status") PlanStatusEnum status);

    /**
     * 根据状态和优先级查询 (用于调度器)
     */
    List<AgentPlanPO> selectByStatusAndPriority(@Param("status") PlanStatusEnum status);

    /**
     * 按状态分页查询。
     */
    List<AgentPlanPO> selectByStatusPaged(@Param("status") PlanStatusEnum status,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /**
     * 查询所有计划
     */
    List<AgentPlanPO> selectAll();

    /**
     * 根据 Workflow Definition ID 查询
     */
    List<AgentPlanPO> selectByWorkflowDefinitionId(@Param("workflowDefinitionId") Long workflowDefinitionId);
}
