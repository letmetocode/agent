package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.VectorStoreTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 向量存储注册表 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorStoreRegistryPO {

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
     * 连接配置 (JSONB)
     */
    private String connectionConfig;

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
}
