package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.SopStructureEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SOP 模板 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SopTemplatePO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 分类
     */
    private String category;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 触发描述 (Planner 语义检索用)
     */
    private String triggerDescription;

    /**
     * 结构类型 (CHAIN 或 DAG)
     */
    private SopStructureEnum structureType;

    /**
     * 图定义 (JSONB，核心流程图 Nodes/Edges)
     */
    private String graphDefinition;

    /**
     * 输入模式 (JSONB)
     */
    private String inputSchema;

    /**
     * 默认配置 (JSONB)
     */
    private String defaultConfig;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
