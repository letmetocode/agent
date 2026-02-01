package com.getoffer.domain.agent.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Agent-工具关联关系领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentToolRelationEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 代理 ID
     */
    private Long agentId;

    /**
     * 工具 ID
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

    /**
     * 验证关联关系是否有效
     */
    public void validate() {
        if (agentId == null) {
            throw new IllegalStateException("Agent ID cannot be null");
        }
        if (toolId == null) {
            throw new IllegalStateException("Tool ID cannot be null");
        }
    }

    /**
     * 判断是否相等。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentToolRelationEntity that = (AgentToolRelationEntity) o;
        return Objects.equals(agentId, that.agentId) && Objects.equals(toolId, that.toolId);
    }

    /**
     * 计算哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(agentId, toolId);
    }
}
