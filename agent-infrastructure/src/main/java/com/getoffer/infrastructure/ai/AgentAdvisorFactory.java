package com.getoffer.infrastructure.ai;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.infrastructure.ai.config.AgentAdvisorConfig;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI Advisor 工厂类。
 * <p>
 * 根据Agent配置构建Advisor链，支持：
 * <ul>
 *   <li>ToolCallAdvisor：工具调用管理</li>
 *   <li>ChatMemoryAdvisor：聊天记忆管理（Message或Prompt模式）</li>
 *   <li>QuestionAnswerAdvisor（RAG）：向量检索增强</li>
 *   <li>SimpleLoggerAdvisor：请求/响应日志记录</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
@Slf4j
@Component
public class AgentAdvisorFactory {

    private final ToolCallingManager toolCallingManager;
    private final ObjectProvider<ChatMemory> chatMemoryProvider;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ListableBeanFactory beanFactory;
    private final JsonCodec jsonCodec;

    /**
     * 构造 AgentAdvisorFactory 实例。
     *
     * @param toolCallingManager 工具调用管理器
     * @param chatMemoryProvider 聊天记忆提供者
     * @param vectorStoreProvider 向量存储提供者
     * @param beanFactory Spring Bean工厂
     */
    public AgentAdvisorFactory(ToolCallingManager toolCallingManager,
                               ObjectProvider<ChatMemory> chatMemoryProvider,
                               ObjectProvider<VectorStore> vectorStoreProvider,
                               ListableBeanFactory beanFactory,
                               JsonCodec jsonCodec) {
        this.toolCallingManager = toolCallingManager;
        this.chatMemoryProvider = chatMemoryProvider;
        this.vectorStoreProvider = vectorStoreProvider;
        this.beanFactory = beanFactory;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 根据Agent配置构建Advisor链。
     * <p>
     * 从Agent的advisorConfig中读取配置，按顺序构建：
     * <ol>
     *   <li>ToolCallAdvisor（如果配置启用或有工具）</li>
     *   <li>ChatMemoryAdvisor（memory配置）</li>
     *   <li>QuestionAnswerAdvisor/RAG（rag配置）</li>
     *   <li>SimpleLoggerAdvisor（logger配置）</li>
     * </ol>
     * </p>
     *
     * @param agent Agent实体对象
     * @param conversationId 会话ID
     * @param hasTools 是否有可用工具
     * @return Advisor列表
     */
    public List<Advisor> buildAdvisors(AgentRegistryEntity agent, String conversationId, boolean hasTools) {
        AgentAdvisorConfig advisorConfig = resolveAdvisorConfig(agent);
        List<Advisor> advisors = new ArrayList<>();

        AgentAdvisorConfig.ToolConfig toolConfig = advisorConfig == null ? null : advisorConfig.getTool();
        if (shouldEnableToolAdvisor(toolConfig, hasTools)) {
            ToolCallAdvisor.Builder<?> builder = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager);
            if (toolConfig != null && toolConfig.getOrder() != null) {
                builder.advisorOrder(toolConfig.getOrder());
            }
            advisors.add(builder.build());
        }

        AgentAdvisorConfig.MemoryConfig memoryConfig = advisorConfig == null ? null : advisorConfig.getMemory();
        if (isEnabled(memoryConfig)) {
            ChatMemory chatMemory = resolveChatMemory(memoryConfig);
            if (chatMemory == null) {
                log.warn("ChatMemory bean not found; memory advisor disabled");
            } else {
                String resolvedConversationId = resolveConversationId(conversationId);
                String memoryType = memoryConfig.getType();
                Integer order = memoryConfig.getOrder();
                if ("prompt".equalsIgnoreCase(memoryType)) {
                    PromptChatMemoryAdvisor.Builder builder = PromptChatMemoryAdvisor.builder(chatMemory)
                            .conversationId(resolvedConversationId);
                    if (order != null) {
                        builder.order(order);
                    }
                    String template = memoryConfig.getSystemPromptTemplate();
                    if (StringUtils.isNotBlank(template)) {
                        builder.systemPromptTemplate(new PromptTemplate(template));
                    }
                    advisors.add(builder.build());
                } else {
                    MessageChatMemoryAdvisor.Builder builder = MessageChatMemoryAdvisor.builder(chatMemory)
                            .conversationId(resolvedConversationId);
                    if (order != null) {
                        builder.order(order);
                    }
                    advisors.add(builder.build());
                }
            }
        }

        AgentAdvisorConfig.RagConfig ragConfig = advisorConfig == null ? null : advisorConfig.getRag();
        if (isEnabled(ragConfig)) {
            VectorStore vectorStore = resolveVectorStore(ragConfig);
            if (vectorStore == null) {
                log.warn("VectorStore bean not found; RAG advisor disabled");
            } else {
                SearchRequest searchRequest = buildSearchRequest(ragConfig);
                QuestionAnswerAdvisor.Builder builder = QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest);
                if (ragConfig != null && ragConfig.getOrder() != null) {
                    builder.order(ragConfig.getOrder());
                }
                String template = ragConfig.getPromptTemplate();
                if (StringUtils.isNotBlank(template)) {
                    builder.promptTemplate(new PromptTemplate(template));
                }
                advisors.add(builder.build());
            }
        }

        AgentAdvisorConfig.LoggerConfig loggerConfig = advisorConfig == null ? null : advisorConfig.getLogger();
        if (isEnabled(loggerConfig)) {
            SimpleLoggerAdvisor.Builder builder = SimpleLoggerAdvisor.builder();
            if (loggerConfig != null && loggerConfig.getOrder() != null) {
                builder.order(loggerConfig.getOrder());
            }
            advisors.add(builder.build());
        }

        return advisors;
    }

    /**
     * 判断是否启用工具顾问。
     */
    private boolean shouldEnableToolAdvisor(AgentAdvisorConfig.ToolConfig toolConfig, boolean hasTools) {
        if (toolConfig == null) {
            return hasTools;
        }
        return isEnabled(toolConfig);
    }

    /**
     * 构建检索请求。
     */
    private SearchRequest buildSearchRequest(AgentAdvisorConfig.RagConfig ragConfig) {
        SearchRequest.Builder builder = SearchRequest.builder();
        if (ragConfig != null && ragConfig.getTopK() != null) {
            builder.topK(ragConfig.getTopK());
        }
        if (ragConfig != null && ragConfig.getSimilarityThreshold() != null) {
            builder.similarityThreshold(ragConfig.getSimilarityThreshold());
        }
        String filterExpression = ragConfig == null ? null : ragConfig.getFilterExpression();
        if (StringUtils.isNotBlank(filterExpression)) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    /**
     * 解析向量存储。
     */
    private VectorStore resolveVectorStore(AgentAdvisorConfig.RagConfig ragConfig) {
        String storeName = ragConfig == null ? null : ragConfig.getVectorStoreName();
        if (StringUtils.isNotBlank(storeName)) {
            try {
                return beanFactory.getBean(storeName, VectorStore.class);
            } catch (Exception ex) {
                log.warn("VectorStore bean '{}' not found", storeName);
            }
        }
        return vectorStoreProvider.getIfAvailable();
    }

    /**
     * 检查是否启用。
     */
    private boolean isEnabled(AgentAdvisorConfig.ToolConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /**
     * 检查是否启用。
     */
    private boolean isEnabled(AgentAdvisorConfig.MemoryConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /**
     * 检查是否启用。
     */
    private boolean isEnabled(AgentAdvisorConfig.RagConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /**
     * 检查是否启用。
     */
    private boolean isEnabled(AgentAdvisorConfig.LoggerConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /**
     * 解析会话 ID。
     */
    private String resolveConversationId(String conversationId) {
        if (StringUtils.isBlank(conversationId)) {
            return ChatMemory.DEFAULT_CONVERSATION_ID;
        }
        return conversationId;
    }

    /**
     * 解析 ChatMemory。
     */
    private ChatMemory resolveChatMemory(AgentAdvisorConfig.MemoryConfig memoryConfig) {
        String beanName = memoryConfig == null ? null : memoryConfig.getBeanName();
        if (StringUtils.isNotBlank(beanName)) {
            try {
                return beanFactory.getBean(beanName, ChatMemory.class);
            } catch (Exception ex) {
                log.warn("ChatMemory bean '{}' not found", beanName);
            }
        }
        return chatMemoryProvider.getIfAvailable();
    }

    private AgentAdvisorConfig resolveAdvisorConfig(AgentRegistryEntity agent) {
        if (agent == null || agent.getAdvisorConfig() == null || agent.getAdvisorConfig().isEmpty()) {
            return new AgentAdvisorConfig();
        }
        AgentAdvisorConfig config = jsonCodec.convert(agent.getAdvisorConfig(), AgentAdvisorConfig.class);
        return config == null ? new AgentAdvisorConfig() : config;
    }
}
