package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 注册表 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRegistryPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 业务唯一标识 (如 'java_coder')
     */
    private String key;

    /**
     * Agent 名称
     */
    private String name;

    /**
     * 模型提供商 (如 'openai', 'anthropic')
     */
    private String modelProvider;

    /**
     * 模型名称 (如 'gpt-4', 'claude-3-opus')
     */
    private String modelName;

    /**
     * 模型选项 (JSONB 存储，如 {"temperature": 0.7})
     */
    private String modelOptions;

    /**
     * 基础人设 (System Prompt)
     */
    private String baseSystemPrompt;

    /**
     * Spring AI Advisors 配置 (JSONB)
     */
    private String advisorConfig;

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
