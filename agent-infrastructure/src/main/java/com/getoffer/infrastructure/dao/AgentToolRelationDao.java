package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentToolRelationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent-工具关联关系 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentToolRelationDao {

    /**
     * 插入关联关系
     */
    int insert(AgentToolRelationPO po);

    /**
     * 根据 Agent ID 和 Tool ID 删除
     */
    int delete(@Param("agentId") Long agentId, @Param("toolId") Long toolId);

    /**
     * 根据 Agent ID 删除所有关联
     */
    int deleteByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据 Tool ID 删除所有关联
     */
    int deleteByToolId(@Param("toolId") Long toolId);

    /**
     * 根据 Agent ID 查询关联的工具
     */
    List<AgentToolRelationPO> selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据 Tool ID 查询关联的 Agent
     */
    List<AgentToolRelationPO> selectByToolId(@Param("toolId") Long toolId);

    /**
     * 查询所有关联关系
     */
    List<AgentToolRelationPO> selectAll();

    /**
     * 检查关联关系是否存在
     */
    boolean exists(@Param("agentId") Long agentId, @Param("toolId") Long toolId);

    /**
     * 批量插入关联关系
     */
    int batchInsert(@Param("agentId") Long agentId, @Param("toolIds") List<Long> toolIds);
}
