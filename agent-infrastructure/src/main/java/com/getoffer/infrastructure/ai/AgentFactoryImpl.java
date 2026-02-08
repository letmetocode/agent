package com.getoffer.infrastructure.ai;

import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.infrastructure.mcp.McpClientManager;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ToolTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 工厂实现类。
 * <p>
 * 负责：
 * <ul>
 *   <li>根据Agent配置创建Spring AI ChatClient实例</li>
 *   <li>解析并配置Agent可用的工具（Spring Bean和MCP工具）</li>
 *   <li>构建ChatOptions和Advisor链</li>
 *   <li>管理不同模型提供商的配置差异</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
@Slf4j
@Component
public class AgentFactoryImpl implements IAgentFactory {

    private final IAgentRegistryRepository agentRegistryRepository;
    private final IAgentToolCatalogRepository agentToolCatalogRepository;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ListableBeanFactory beanFactory;
    private final AgentAdvisorFactory advisorFactory;
    private final McpClientManager mcpClientManager;
    private final JsonCodec jsonCodec;
    private final boolean legacyOptionsToolWrite;

    /**
     * 构造 AgentFactoryImpl 实例。
     *
     * @param agentRegistryRepository Agent注册表仓储
     * @param agentToolCatalogRepository 工具目录仓储
     * @param chatModelProvider ChatModel提供者
     * @param beanFactory Spring Bean工厂
     * @param advisorFactory Advisor工厂
     * @param mcpClientManager MCP客户端管理器
     */
    public AgentFactoryImpl(IAgentRegistryRepository agentRegistryRepository,
                            IAgentToolCatalogRepository agentToolCatalogRepository,
                            ObjectProvider<ChatModel> chatModelProvider,
                            ListableBeanFactory beanFactory,
                            AgentAdvisorFactory advisorFactory,
                            McpClientManager mcpClientManager,
                            JsonCodec jsonCodec,
                            @Value("${agent.tool.config.legacy-options-write:false}") boolean legacyOptionsToolWrite) {
        this.agentRegistryRepository = agentRegistryRepository;
        this.agentToolCatalogRepository = agentToolCatalogRepository;
        this.chatModelProvider = chatModelProvider;
        this.beanFactory = beanFactory;
        this.advisorFactory = advisorFactory;
        this.mcpClientManager = mcpClientManager;
        this.jsonCodec = jsonCodec;
        this.legacyOptionsToolWrite = legacyOptionsToolWrite;
    }

    /**
     * 根据业务唯一标识创建Agent。
     *
     * @param agentKey 业务唯一标识
     * @param conversationId 会话ID
     * @return ChatClient实例
     * @throws IllegalArgumentException 如果agentKey为空
     * @throws IllegalStateException 如果Agent不存在或未激活
     */
    @Override
    public ChatClient createAgent(String agentKey, String conversationId) {
        if (StringUtils.isBlank(agentKey)) {
            throw new IllegalArgumentException("agentKey cannot be blank");
        }
        AgentRegistryEntity agent = agentRegistryRepository.findByKey(agentKey);
        if (agent == null) {
            throw new IllegalStateException("Agent not found: " + agentKey);
        }
        return createAgent(agent, conversationId, null);
    }

    /**
     * 根据主键ID创建Agent。
     *
     * @param agentId Agent主键ID
     * @param conversationId 会话ID
     * @return ChatClient实例
     * @throws IllegalArgumentException 如果agentId为空
     * @throws IllegalStateException 如果Agent不存在或未激活
     */
    @Override
    public ChatClient createAgent(Long agentId, String conversationId) {
        if (agentId == null) {
            throw new IllegalArgumentException("agentId cannot be null");
        }
        AgentRegistryEntity agent = agentRegistryRepository.findById(agentId);
        if (agent == null) {
            throw new IllegalStateException("Agent not found: " + agentId);
        }
        return createAgent(agent, conversationId, null);
    }

    /**
     * 根据Agent实体创建ChatClient实例。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>验证Agent配置和激活状态</li>
     *   <li>解析Agent关联的工具（Spring Bean和MCP工具）</li>
     *   <li>构建工具上下文</li>
     *   <li>构建ChatOptions（支持OpenAI和其他提供商）</li>
     *   <li>构建Advisor链</li>
     *   <li>组装并返回ChatClient</li>
     * </ol>
     * </p>
     *
     * @param agent Agent实体对象
     * @param conversationId 会话ID
     * @return 配置完成的ChatClient实例
     * @throws IllegalArgumentException 如果agent为空
     * @throws IllegalStateException 如果Agent未激活或配置无效
     */
    @Override
    public ChatClient createAgent(AgentRegistryEntity agent, String conversationId) {
        return createAgent(agent, conversationId, null);
    }

    @Override
    public ChatClient createAgent(String agentKey, String conversationId, String systemPromptSuffix) {
        if (StringUtils.isBlank(agentKey)) {
            throw new IllegalArgumentException("agentKey cannot be blank");
        }
        AgentRegistryEntity agent = agentRegistryRepository.findByKey(agentKey);
        if (agent == null) {
            throw new IllegalStateException("Agent not found: " + agentKey);
        }
        return createAgent(agent, conversationId, systemPromptSuffix);
    }

    @Override
    public ChatClient createAgent(Long agentId, String conversationId, String systemPromptSuffix) {
        if (agentId == null) {
            throw new IllegalArgumentException("agentId cannot be null");
        }
        AgentRegistryEntity agent = agentRegistryRepository.findById(agentId);
        if (agent == null) {
            throw new IllegalStateException("Agent not found: " + agentId);
        }
        return createAgent(agent, conversationId, systemPromptSuffix);
    }

    @Override
    public ChatClient createAgent(AgentRegistryEntity agent, String conversationId, String systemPromptSuffix) {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }
        agent.validate();
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new IllegalStateException("Agent is inactive: " + agent.getKey());
        }

        ResolvedTools tools = resolveTools(agent.getId());
        Map<String, Object> toolContext = buildToolContext(agent, conversationId);
        ChatOptions options = buildChatOptions(agent);
        if (legacyOptionsToolWrite && options instanceof OpenAiChatOptions) {
            applyLegacyToolOptions((OpenAiChatOptions) options, tools.getToolNames(), toolContext);
        }
        List<Advisor> advisors = advisorFactory.buildAdvisors(agent, conversationId, tools.hasTools());

        ChatModel chatModel = resolveChatModel(agent);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        String systemPrompt = buildSystemPrompt(agent.getBaseSystemPrompt(), systemPromptSuffix);
        if (StringUtils.isNotBlank(systemPrompt)) {
            builder.defaultSystem(systemPrompt);
        }
        if (options != null) {
            builder.defaultOptions(options);
        }
        if (!tools.getToolNames().isEmpty()) {
            builder.defaultToolNames(tools.getToolNames().toArray(new String[0]));
        }
        if (!tools.getToolCallbacks().isEmpty()) {
            builder.defaultToolCallbacks(tools.getToolCallbacks());
        }
        if (!toolContext.isEmpty()) {
            builder.defaultToolContext(toolContext);
        }
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors);
        }
        return builder.build();
    }

    private String buildSystemPrompt(String basePrompt, String suffix) {
        String trimmedSuffix = StringUtils.trimToNull(suffix);
        if (StringUtils.isBlank(basePrompt)) {
            return trimmedSuffix;
        }
        if (trimmedSuffix == null) {
            return basePrompt;
        }
        return basePrompt + "\n" + trimmedSuffix;
    }

    /**
     * 构建对话参数。
     */
    private ChatOptions buildChatOptions(AgentRegistryEntity agent) {
        String provider = agent.getModelProvider();
        if (StringUtils.isBlank(provider) || "openai".equalsIgnoreCase(provider)) {
            return buildOpenAiChatOptions(agent);
        }
        log.warn("Unsupported model provider '{}', fallback to basic ChatOptions", provider);
        return buildBasicOptions(agent);
    }

    /**
     * 构建基础参数。
     */
    private ChatOptions buildBasicOptions(AgentRegistryEntity agent) {
        Map<String, Object> modelOptions = agent.getModelOptions();
        ChatOptions.Builder builder = ChatOptions.builder();
        if (StringUtils.isNotBlank(agent.getModelName())) {
            builder.model(agent.getModelName());
        }
        if (modelOptions == null || modelOptions.isEmpty()) {
            return builder.build();
        }
        setDouble(modelOptions, "temperature", builder::temperature);
        setDouble(modelOptions, "topP", builder::topP);
        setInteger(modelOptions, "topK", builder::topK);
        setInteger(modelOptions, "maxTokens", builder::maxTokens);
        setDouble(modelOptions, "presencePenalty", builder::presencePenalty);
        setDouble(modelOptions, "frequencyPenalty", builder::frequencyPenalty);
        @SuppressWarnings("unchecked")
        List<String> stop = castList(modelOptions.get("stopSequences"));
        if (stop == null) {
            stop = castList(modelOptions.get("stop"));
        }
        if (stop != null) {
            builder.stopSequences(stop);
        }
        return builder.build();
    }

    /**
     * 构建 OpenAI 对话参数。
     */
    private OpenAiChatOptions buildOpenAiChatOptions(AgentRegistryEntity agent) {
        OpenAiChatOptions options = new OpenAiChatOptions();
        Map<String, Object> modelOptions = agent.getModelOptions();
        boolean containsToolConfig = containsToolConfig(modelOptions);
        if (modelOptions != null && !modelOptions.isEmpty()) {
            try {
                OpenAiChatOptions resolved = jsonCodec.convert(modelOptions, OpenAiChatOptions.class);
                if (resolved != null) {
                    options = resolved;
                }
            } catch (Exception ex) {
                log.warn("Failed to parse modelOptions for agent '{}': {}", agent.getKey(), ex.getMessage());
            }
        }
        if (StringUtils.isNotBlank(agent.getModelName())) {
            options.setModel(agent.getModelName());
        }
        if (!legacyOptionsToolWrite) {
            clearToolOptions(options);
            if (containsToolConfig) {
                log.debug("Tool config in modelOptions ignored for agent '{}'; ChatClient.Builder is source of truth.",
                        agent.getKey());
            }
        }
        return options;
    }

    /**
     * 兼容模式：同时写入 OpenAiChatOptions 的工具配置。
     * 默认关闭，仅用于紧急回滚。
     */
    private void applyLegacyToolOptions(OpenAiChatOptions options,
                                        List<String> toolNames,
                                        Map<String, Object> toolContext) {
        if (options == null) {
            return;
        }
        if (toolNames != null && !toolNames.isEmpty()) {
            options.setToolNames(new HashSet<>(toolNames));
        }
        if (toolContext != null && !toolContext.isEmpty()) {
            options.setToolContext(new HashMap<>(toolContext));
        }
    }

    /**
     * 单一来源策略：工具配置仅由 ChatClient.Builder 负责。
     */
    private void clearToolOptions(OpenAiChatOptions options) {
        if (options == null) {
            return;
        }
        options.setToolNames(Collections.emptySet());
        options.setToolContext(Collections.emptyMap());
    }

    private boolean containsToolConfig(Map<String, Object> modelOptions) {
        if (modelOptions == null || modelOptions.isEmpty()) {
            return false;
        }
        for (String key : modelOptions.keySet()) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            String normalized = key.trim().toLowerCase();
            if ("toolnames".equals(normalized)
                    || "tool_names".equals(normalized)
                    || "toolcontext".equals(normalized)
                    || "tool_context".equals(normalized)
                    || "tools".equals(normalized)
                    || "defaulttoolnames".equals(normalized)
                    || "defaulttoolcontext".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析工具。
     */
    private ResolvedTools resolveTools(Long agentId) {
        if (agentId == null) {
            return ResolvedTools.empty();
        }
        List<AgentToolCatalogEntity> tools = agentToolCatalogRepository.findEnabledByAgentId(agentId);
        if (tools == null || tools.isEmpty()) {
            return ResolvedTools.empty();
        }

        Set<String> toolNames = new LinkedHashSet<>();
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (AgentToolCatalogEntity tool : tools) {
            if (tool == null || !Boolean.TRUE.equals(tool.getIsActive())) {
                continue;
            }
            ToolTypeEnum toolType = tool.getType();
            if (toolType == ToolTypeEnum.MCP_FUNCTION) {
                ToolCallback callback = buildMcpToolCallback(tool);
                if (callback != null) {
                    toolCallbacks.add(callback);
                }
                continue;
            }
            String toolName = resolveSpringBeanToolName(tool);
            if (StringUtils.isNotBlank(toolName)) {
                toolNames.add(toolName);
            }
        }
        return new ResolvedTools(new ArrayList<>(toolNames), toolCallbacks);
    }

    /**
     * 构建工具上下文。
     */
    private Map<String, Object> buildToolContext(AgentRegistryEntity agent, String conversationId) {
        Map<String, Object> context = new HashMap<>();
        if (agent.getId() != null) {
            context.put("agentId", agent.getId());
        }
        if (StringUtils.isNotBlank(agent.getKey())) {
            context.put("agentKey", agent.getKey());
        }
        if (StringUtils.isNotBlank(conversationId)) {
            context.put("conversationId", conversationId);
        }
        return context;
    }

    /**
     * 解析 ChatModel。
     */
    private ChatModel resolveChatModel(AgentRegistryEntity agent) {
        String provider = agent.getModelProvider();
        if (StringUtils.isNotBlank(provider) && beanFactory.containsBean(provider)) {
            try {
                return beanFactory.getBean(provider, ChatModel.class);
            } catch (Exception ex) {
                log.warn("ChatModel bean '{}' not found, fallback to default", provider);
            }
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("ChatModel bean not found");
        }
        return chatModel;
    }

    /**
     * 解析 Spring Bean 工具名。
     */
    private String resolveSpringBeanToolName(AgentToolCatalogEntity tool) {
        Map<String, Object> toolConfig = tool.getToolConfig();
        if (toolConfig != null && !toolConfig.isEmpty()) {
            String beanName = getString(toolConfig, "beanName", "bean_name", "toolBeanName", "tool_bean_name");
            if (StringUtils.isNotBlank(beanName)) {
                return beanName;
            }
        }
        return tool.getName();
    }

    /**
     * 构建 MCP 工具回调。
     */
    private ToolCallback buildMcpToolCallback(AgentToolCatalogEntity tool) {
        if (tool == null || StringUtils.isBlank(tool.getName())) {
            return null;
        }
        Map<String, Object> serverConfig = resolveMcpServerConfig(tool);
        if (serverConfig == null || serverConfig.isEmpty()) {
            log.warn("MCP server config not found for tool '{}'", tool.getName());
            return null;
        }
        ToolCallback callback = mcpClientManager.getToolCallback(serverConfig, tool.getName());
        if (callback == null) {
            log.warn("MCP tool callback not found for tool '{}'", tool.getName());
        }
        return callback;
    }

    /**
     * 解析 MCP Server 配置。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveMcpServerConfig(AgentToolCatalogEntity tool) {
        Map<String, Object> toolConfig = tool.getToolConfig();
        if (toolConfig == null || toolConfig.isEmpty()) {
            return Collections.emptyMap();
        }
        Object nested = toolConfig.get("mcpServerConfig");
        if (nested == null) {
            nested = toolConfig.get("mcp_server_config");
        }
        if (nested == null) {
            nested = toolConfig.get("serverConfig");
        }
        if (nested == null) {
            nested = toolConfig.get("server");
        }
        if (nested instanceof Map<?, ?>) {
            return (Map<String, Object>) nested;
        }
        return toolConfig;
    }

    /**
     * 设置双精度参数。
     */
    private void setDouble(Map<String, Object> options, String key, java.util.function.Consumer<Double> consumer) {
        Object value = options.get(key);
        if (value instanceof Number) {
            consumer.accept(((Number) value).doubleValue());
        } else if (value != null) {
            try {
                consumer.accept(Double.parseDouble(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * 设置整数参数。
     */
    private void setInteger(Map<String, Object> options, String key, java.util.function.Consumer<Integer> consumer) {
        Object value = options.get(key);
        if (value instanceof Number) {
            consumer.accept(((Number) value).intValue());
        } else if (value != null) {
            try {
                consumer.accept(Integer.parseInt(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * 转换为列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> castList(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 获取字符串配置。
     */
    private String getString(Map<String, Object> options, String... keys) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = options.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * 工具解析结果
     */
    private static class ResolvedTools {

        private final List<String> toolNames;
        private final List<ToolCallback> toolCallbacks;

        /**
         * 创建工具解析结果。
         */
        ResolvedTools(List<String> toolNames, List<ToolCallback> toolCallbacks) {
            this.toolNames = toolNames == null ? Collections.emptyList() : toolNames;
            this.toolCallbacks = toolCallbacks == null ? Collections.emptyList() : toolCallbacks;
        }

        /**
         * 获取空工具结果。
         */
        static ResolvedTools empty() {
            return new ResolvedTools(Collections.emptyList(), Collections.emptyList());
        }

        /**
         * 判断是否包含工具。
         */
        boolean hasTools() {
            return !toolNames.isEmpty() || !toolCallbacks.isEmpty();
        }

        /**
         * 获取工具名称。
         */
        List<String> getToolNames() {
            return toolNames;
        }

        /**
         * 获取工具回调。
         */
        List<ToolCallback> getToolCallbacks() {
            return toolCallbacks;
        }
    }
}
