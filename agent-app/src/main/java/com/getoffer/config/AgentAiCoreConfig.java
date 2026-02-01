package com.getoffer.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.util.json.schema.SchemaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Spring AI 核心组件配置类。
 * <p>
 * 配置Spring AI框架所需的核心Bean，包括：
 * <ul>
 *   <li>ToolCallbackResolver: 工具回调解析器，负责解析和注册Agent可用的工具</li>
 *   <li>ToolCallingManager: 工具调用管理器，管理LLM的工具调用流程</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Configuration
public class AgentAiCoreConfig {

    /**
     * 创建工具回调解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext) {
        return SpringBeanToolCallbackResolver.builder()
                .applicationContext(applicationContext)
                .schemaType(SchemaType.JSON_SCHEMA)
                .build();
    }

    /**
     * 创建工具调用管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolCallingManager toolCallingManager(ToolCallbackResolver toolCallbackResolver,
                                                 ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        DefaultToolCallingManager.Builder builder = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver);
        ObservationRegistry registry = observationRegistryProvider.getIfAvailable();
        if (registry != null) {
            builder.observationRegistry(registry);
        }
        return builder.build();
    }
}
