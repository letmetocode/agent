package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity;

import java.util.List;

/**
 * 向量存储注册表仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IVectorStoreRegistryRepository {

    /**
     * 保存向量存储配置
     */
    VectorStoreRegistryEntity save(VectorStoreRegistryEntity entity);

    /**
     * 更新向量存储配置
     */
    VectorStoreRegistryEntity update(VectorStoreRegistryEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    VectorStoreRegistryEntity findById(Long id);

    /**
     * 根据名称查询
     */
    VectorStoreRegistryEntity findByName(String name);

    /**
     * 查询所有向量存储
     */
    List<VectorStoreRegistryEntity> findAll();
}
