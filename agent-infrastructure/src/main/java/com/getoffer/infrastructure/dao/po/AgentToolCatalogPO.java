package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.ToolTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具目录 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCatalogPO {

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
     * 工具配置 (JSONB)
     */
    private String toolConfig;

    /**
     * 输入参数定义 (JSON Schema，JSONB)
     */
    private String inputSchema;

    /**
     * 输出参数定义 (JSON Schema，JSONB)
     */
    private String outputSchema;

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
