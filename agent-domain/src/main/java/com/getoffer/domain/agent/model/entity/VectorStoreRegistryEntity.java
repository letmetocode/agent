package com.getoffer.domain.agent.model.entity;

import com.getoffer.types.enums.VectorStoreTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 向量存储注册表领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class VectorStoreRegistryEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 向量存储名称
     */
    private String name;

    /**
     * 向量存储类型
     */
    private VectorStoreTypeEnum storeType;

    /**
     * 连接配置 (解析后的 Map)
     */
    private Map<String, Object> connectionConfig;

    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 向量维度
     */
    private Integer dimension;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 验证向量存储配置是否有效
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Vector store name cannot be empty");
        }
        if (storeType == null) {
            throw new IllegalStateException("Vector store type cannot be empty");
        }
        if (connectionConfig == null || connectionConfig.isEmpty()) {
            throw new IllegalStateException("Connection config cannot be empty");
        }
    }
}
