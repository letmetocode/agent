package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.VectorStoreRegistryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 向量存储注册表 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface VectorStoreRegistryDao {

    /**
     * 插入向量存储配置
     */
    int insert(VectorStoreRegistryPO po);

    /**
     * 根据 ID 更新
     */
    int update(VectorStoreRegistryPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    VectorStoreRegistryPO selectById(@Param("id") Long id);

    /**
     * 根据名称查询
     */
    VectorStoreRegistryPO selectByName(@Param("name") String name);

    /**
     * 查询所有向量存储
     */
    List<VectorStoreRegistryPO> selectAll();
}
