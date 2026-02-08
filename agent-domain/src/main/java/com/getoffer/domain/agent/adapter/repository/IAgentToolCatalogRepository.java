package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.types.enums.ToolTypeEnum;

import java.util.Collection;
import java.util.List;

/**
 * 工具目录仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentToolCatalogRepository {

    /**
     * 保存工具
     */
    AgentToolCatalogEntity save(AgentToolCatalogEntity entity);

    /**
     * 更新工具
     */
    AgentToolCatalogEntity update(AgentToolCatalogEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentToolCatalogEntity findById(Long id);

    /**
     * 根据工具名查询
     */
    AgentToolCatalogEntity findByName(String name);

    /**
     * 查询所有工具
     */
    List<AgentToolCatalogEntity> findAll();

    /**
     * 根据类型查询
     */
    List<AgentToolCatalogEntity> findByType(ToolTypeEnum type);

    /**
     * 按 Agent 查询启用工具（含关联启用状态），并按优先级稳定排序。
     */
    List<AgentToolCatalogEntity> findEnabledByAgentId(Long agentId);

    /**
     * 按 ID 集合查询。
     */
    List<AgentToolCatalogEntity> findByIds(Collection<Long> ids);

    /**
     * 检查工具名是否存在
     */
    boolean existsByName(String name);
}
