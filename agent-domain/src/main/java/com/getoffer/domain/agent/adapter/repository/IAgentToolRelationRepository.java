package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.AgentToolRelationEntity;

import java.util.List;

/**
 * Agent-工具关联关系仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentToolRelationRepository {

    /**
     * 保存关联关系
     */
    AgentToolRelationEntity save(AgentToolRelationEntity entity);

    /**
     * 根据 Agent ID 和 Tool ID 删除
     */
    boolean delete(Long agentId, Long toolId);

    /**
     * 根据 Agent ID 删除所有关联
     */
    boolean deleteByAgentId(Long agentId);

    /**
     * 根据 Tool ID 删除所有关联
     */
    boolean deleteByToolId(Long toolId);

    /**
     * 根据 Agent ID 查询关联的工具
     */
    List<AgentToolRelationEntity> findByAgentId(Long agentId);

    /**
     * 根据 Tool ID 查询关联的 Agent
     */
    List<AgentToolRelationEntity> findByToolId(Long toolId);

    /**
     * 查询所有关联关系
     */
    List<AgentToolRelationEntity> findAll();

    /**
     * 检查关联关系是否存在
     */
    boolean exists(Long agentId, Long toolId);

    /**
     * 批量保存关联关系
     */
    boolean batchSave(Long agentId, List<Long> toolIds);
}
