package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.types.enums.PlanStatusEnum;

import java.util.List;

/**
 * 执行计划仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentPlanRepository {

    /**
     * 保存执行计划
     */
    AgentPlanEntity save(AgentPlanEntity entity);

    /**
     * 更新执行计划 (带乐观锁)
     */
    AgentPlanEntity update(AgentPlanEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentPlanEntity findById(Long id);

    /**
     * 根据会话 ID 查询
     */
    List<AgentPlanEntity> findBySessionId(Long sessionId);

    /**
     * 根据状态查询
     */
    List<AgentPlanEntity> findByStatus(PlanStatusEnum status);

    /**
     * 根据状态和优先级查询 (用于调度器)
     */
    List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status);

    /**
     * 按状态分页查询。
     */
    List<AgentPlanEntity> findByStatusPaged(PlanStatusEnum status, int offset, int limit);

    /**
     * 查询所有计划
     */
    List<AgentPlanEntity> findAll();

    /**
     * 根据 Workflow Definition ID 查询
     */
    List<AgentPlanEntity> findByWorkflowDefinitionId(Long workflowDefinitionId);

    /**
     * 查询可执行的计划
     */
    List<AgentPlanEntity> findExecutablePlans();
}
