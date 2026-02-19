package com.getoffer.domain.agent.adapter.repository;

import com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /**
     * 查询最近更新的向量存储配置。
     */
    default List<VectorStoreRegistryEntity> findRecent(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<VectorStoreRegistryEntity> stores = findAll();
        if (stores == null || stores.isEmpty()) {
            return Collections.emptyList();
        }
        return stores.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(VectorStoreRegistryEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(VectorStoreRegistryEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
