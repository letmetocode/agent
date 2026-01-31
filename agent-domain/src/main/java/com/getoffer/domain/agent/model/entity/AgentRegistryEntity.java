package com.getoffer.domain.agent.model.entity;

import com.getoffer.types.enums.ResponseCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 注册表领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentRegistryEntity {

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
     * 模型提供商
     */
    private String modelProvider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型选项 (解析后的 Map)
     */
    private Map<String, Object> modelOptions;

    /**
     * 基础人设 (System Prompt)
     */
    private String baseSystemPrompt;

    /**
     * Spring AI Advisors 配置 (解析后的 Map)
     */
    private Map<String, Object> advisorConfig;

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
     * 验证 Agent 配置是否有效
     */
    public void validate() {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("Agent key cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Agent name cannot be empty");
        }
        if (modelProvider == null || modelProvider.trim().isEmpty()) {
            throw new IllegalStateException("Model provider cannot be empty");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalStateException("Model name cannot be empty");
        }
    }

    /**
     * 激活 Agent
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 停用 Agent
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新模型配置
     */
    public void updateModelConfig(String modelProvider, String modelName, Map<String, Object> modelOptions) {
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.modelOptions = modelOptions;
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    /**
     * 更新系统提示词
     */
    public void updateSystemPrompt(String systemPrompt) {
        this.baseSystemPrompt = systemPrompt;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新 Advisors 配置
     */
    public void updateAdvisorConfig(Map<String, Object> advisorConfig) {
        this.advisorConfig = advisorConfig;
        this.updatedAt = LocalDateTime.now();
    }
}
