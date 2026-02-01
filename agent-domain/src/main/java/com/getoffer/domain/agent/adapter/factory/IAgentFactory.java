package com.getoffer.domain.agent.adapter.factory;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Agent 工厂接口。
 * <p>
 * 负责根据Agent配置创建Spring AI ChatClient实例。
 * 支持通过Agent Key、Agent ID或Agent实体三种方式创建Agent。
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
public interface IAgentFactory {

    /**
     * 根据业务唯一标识创建Agent。
     *
     * @param agentKey 业务唯一标识（如 'java_coder'）
     * @param conversationId 会话ID
     * @return ChatClient实例
     */
    ChatClient createAgent(String agentKey, String conversationId);

    /**
     * 根据主键ID创建Agent。
     *
     * @param agentId Agent主键ID
     * @param conversationId 会话ID
     * @return ChatClient实例
     */
    ChatClient createAgent(Long agentId, String conversationId);

    /**
     * 根据Agent实体创建Agent。
     *
     * @param agent Agent实体对象
     * @param conversationId 会话ID
     * @return ChatClient实例
     */
    ChatClient createAgent(AgentRegistryEntity agent, String conversationId);
}
