package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;

import java.util.List;

/**
 * Agent 注册表仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentRegistryRepository {

    /**
     * 保存 Agent 配置
     */
    AgentRegistryEntity save(AgentRegistryEntity entity);

    /**
     * 更新 Agent 配置
     */
    AgentRegistryEntity update(AgentRegistryEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentRegistryEntity findById(Long id);

    /**
     * 根据 Key 查询
     */
    AgentRegistryEntity findByKey(String key);

    /**
     * 查询所有 Agent
     */
    List<AgentRegistryEntity> findAll();

    /**
     * 根据激活状态查询
     */
    List<AgentRegistryEntity> findByActive(Boolean isActive);

    /**
     * 根据模型提供商查询
     */
    List<AgentRegistryEntity> findByModelProvider(String modelProvider);

    /**
     * 检查 Key 是否存在
     */
    boolean existsByKey(String key);
}
