package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentRegistryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 注册表 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentRegistryDao {

    /**
     * 插入 Agent 配置
     */
    int insert(AgentRegistryPO po);

    /**
     * 根据 ID 更新
     */
    int update(AgentRegistryPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    AgentRegistryPO selectById(@Param("id") Long id);

    /**
     * 根据 Key 查询
     */
    AgentRegistryPO selectByKey(@Param("key") String key);

    /**
     * 查询所有 Agent
     */
    List<AgentRegistryPO> selectAll();

    /**
     * 根据激活状态查询
     */
    List<AgentRegistryPO> selectByActive(@Param("isActive") Boolean isActive);

    /**
     * 根据模型提供商查询
     */
    List<AgentRegistryPO> selectByModelProvider(@Param("modelProvider") String modelProvider);
}
