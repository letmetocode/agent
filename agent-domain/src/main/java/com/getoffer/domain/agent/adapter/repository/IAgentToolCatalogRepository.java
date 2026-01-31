package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.types.enums.ToolTypeEnum;

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
     * 检查工具名是否存在
     */
    boolean existsByName(String name);
}
