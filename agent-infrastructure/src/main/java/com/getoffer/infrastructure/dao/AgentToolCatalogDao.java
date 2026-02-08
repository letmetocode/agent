package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentToolCatalogPO;
import com.getoffer.infrastructure.dao.po.AgentToolBindingPO;
import com.getoffer.types.enums.ToolTypeEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具目录 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentToolCatalogDao {

    /**
     * 插入工具
     */
    int insert(AgentToolCatalogPO po);

    /**
     * 根据 ID 更新
     */
    int update(AgentToolCatalogPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    AgentToolCatalogPO selectById(@Param("id") Long id);

    /**
     * 根据工具名查询
     */
    AgentToolCatalogPO selectByName(@Param("name") String name);

    /**
     * 查询所有工具
     */
    List<AgentToolCatalogPO> selectAll();

    /**
     * 根据类型查询
     */
    List<AgentToolCatalogPO> selectByType(@Param("type") ToolTypeEnum type);

    /**
     * 按 Agent 查询启用工具绑定（关联+目录）。
     */
    List<AgentToolBindingPO> selectEnabledBindingsByAgentId(@Param("agentId") Long agentId);

    /**
     * 按 ID 集合查询工具。
     */
    List<AgentToolCatalogPO> selectByIds(@Param("ids") List<Long> ids);
}
