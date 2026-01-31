package com.getoffer.domain.planning.model.entity;

import com.getoffer.types.enums.SopStructureEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SOP 模板领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class SopTemplateEntity {

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
     * 触发描述
     */
    private String triggerDescription;

    /**
     * 结构类型
     */
    private SopStructureEnum structureType;

    /**
     * 图定义 (解析后的 Map，核心流程图 Nodes/Edges)
     */
    private Map<String, Object> graphDefinition;

    /**
     * 输入模式 (解析后的 Map)
     */
    private Map<String, Object> inputSchema;

    /**
     * 默认配置 (解析后的 Map)
     */
    private Map<String, Object> defaultConfig;

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

    /**
     * 验证 SOP 模板是否有效
     */
    public void validate() {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalStateException("Category cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Template name cannot be empty");
        }
        if (version == null || version < 1) {
            throw new IllegalStateException("Version must be greater than 0");
        }
        if (triggerDescription == null || triggerDescription.trim().isEmpty()) {
            throw new IllegalStateException("Trigger description cannot be empty");
        }
        if (structureType == null) {
            throw new IllegalStateException("Structure type cannot be null");
        }
        if (graphDefinition == null || graphDefinition.isEmpty()) {
            throw new IllegalStateException("Graph definition cannot be empty");
        }
    }

    /**
     * 激活模板
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 停用模板
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 创建新版本
     */
    public void createNewVersion() {
        this.version++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新图定义
     */
    public void updateGraphDefinition(Map<String, Object> graphDefinition) {
        this.graphDefinition = graphDefinition;
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    /**
     * 检查是否为链式结构
     */
    public boolean isChainStructure() {
        return SopStructureEnum.CHAIN.equals(this.structureType);
    }

    /**
     * 检查是否为 DAG 结构
     */
    public boolean isDagStructure() {
        return SopStructureEnum.DAG.equals(this.structureType);
    }
}
