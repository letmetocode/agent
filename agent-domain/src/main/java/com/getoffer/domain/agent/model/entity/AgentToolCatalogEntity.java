package com.getoffer.domain.agent.model.entity;

import com.getoffer.types.enums.ToolTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工具目录领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentToolCatalogEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 工具名
     */
    private String name;

    /**
     * 工具类型
     */
    private ToolTypeEnum type;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具配置 (解析后的 Map)
     */
    private Map<String, Object> toolConfig;

    /**
     * 输入参数定义 (解析后的 Map，JSON Schema)
     */
    private Map<String, Object> inputSchema;

    /**
     * 输出参数定义 (解析后的 Map，JSON Schema)
     */
    private Map<String, Object> outputSchema;

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
     * 验证工具是否有效
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Tool name cannot be empty");
        }
        if (type == null) {
            throw new IllegalStateException("Tool type cannot be empty");
        }
        if (toolConfig == null || toolConfig.isEmpty()) {
            throw new IllegalStateException("Tool config cannot be empty");
        }
    }

    /**
     * 更新工具描述
     */
    public void updateDescription(String description) {
        this.description = description;
    }

    /**
     * 更新输入模式
     */
    public void updateInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    /**
     * 更新输出模式
     */
    public void updateOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }

    /**
     * 更新工具配置
     */
    public void updateToolConfig(Map<String, Object> toolConfig) {
        this.toolConfig = toolConfig;
    }
}
