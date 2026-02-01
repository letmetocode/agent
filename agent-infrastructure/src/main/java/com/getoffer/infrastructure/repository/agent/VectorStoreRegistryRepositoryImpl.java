package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity;
import com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository;
import com.getoffer.infrastructure.dao.VectorStoreRegistryDao;
import com.getoffer.infrastructure.dao.po.VectorStoreRegistryPO;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储注册表仓储实现类。
 * <p>
 * 负责向量存储配置的持久化操作，包括：
 * <ul>
 *   <li>向量存储配置的增删改查</li>
 *   <li>按名称查询</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（connectionConfig）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class VectorStoreRegistryRepositoryImpl implements IVectorStoreRegistryRepository {

    private final VectorStoreRegistryDao vectorStoreRegistryDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 VectorStoreRegistryRepositoryImpl。
     */
    public VectorStoreRegistryRepositoryImpl(VectorStoreRegistryDao vectorStoreRegistryDao, JsonCodec jsonCodec) {
        this.vectorStoreRegistryDao = vectorStoreRegistryDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public VectorStoreRegistryEntity save(VectorStoreRegistryEntity entity) {
        entity.validate();
        VectorStoreRegistryPO po = toPO(entity);
        vectorStoreRegistryDao.insert(po);
        return toEntity(po);
    }

    /**
     * 更新实体。
     */
    @Override
    public VectorStoreRegistryEntity update(VectorStoreRegistryEntity entity) {
        entity.validate();
        VectorStoreRegistryPO po = toPO(entity);
        vectorStoreRegistryDao.update(po);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return vectorStoreRegistryDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public VectorStoreRegistryEntity findById(Long id) {
        VectorStoreRegistryPO po = vectorStoreRegistryDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按名称查询。
     */
    @Override
    public VectorStoreRegistryEntity findByName(String name) {
        VectorStoreRegistryPO po = vectorStoreRegistryDao.selectByName(name);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 查询全部。
     */
    @Override
    public List<VectorStoreRegistryEntity> findAll() {
        return vectorStoreRegistryDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * PO 转换为 Entity
     */
    private VectorStoreRegistryEntity toEntity(VectorStoreRegistryPO po) {
        if (po == null) {
            return null;
        }
        VectorStoreRegistryEntity entity = new VectorStoreRegistryEntity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setStoreType(po.getStoreType());
        entity.setCollectionName(po.getCollectionName());
        entity.setDimension(po.getDimension());
        entity.setIsActive(po.getIsActive());

        // JSONB 字段转换
        if (po.getConnectionConfig() != null) {
            entity.setConnectionConfig(jsonCodec.readMap(po.getConnectionConfig()));
        }

        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private VectorStoreRegistryPO toPO(VectorStoreRegistryEntity entity) {
        if (entity == null) {
            return null;
        }
        VectorStoreRegistryPO po = VectorStoreRegistryPO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .storeType(entity.getStoreType())
                .collectionName(entity.getCollectionName())
                .dimension(entity.getDimension())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getConnectionConfig() != null) {
            po.setConnectionConfig(jsonCodec.writeValue(entity.getConnectionConfig()));
        }

        return po;
    }
}
