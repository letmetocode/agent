package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent-工具关联关系 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolRelationPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * Agent ID (关联 agent_registry.id)
     */
    private Long agentId;

    /**
     * 工具 ID (关联 agent_tool_catalog.id)
     */
    private Long toolId;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
