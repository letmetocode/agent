package com.getoffer.infrastructure.ai.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Advisor 配置对象。
 *
 * @author getoffer
 * @since 2026-02-01
 */
@Data
public class AgentAdvisorConfig {

    @JsonAlias({"toolCall", "tool_call"})
    private ToolConfig tool;

    private MemoryConfig memory;

    private RagConfig rag;

    @JsonAlias({"simpleLogger", "simple_logger"})
    private LoggerConfig logger;

    @Data
    public static class ToolConfig {
        private Boolean enabled;
        private Integer order;
    }

    @Data
    public static class MemoryConfig {
        private Boolean enabled;
        private Integer order;
        private String type;
        @JsonAlias({"systemPromptTemplate", "system_prompt_template"})
        private String systemPromptTemplate;
        @JsonAlias({"bean_name", "chatMemory", "chatMemoryName", "memoryName"})
        private String beanName;
    }

    @Data
    public static class RagConfig {
        private Boolean enabled;
        private Integer order;
        @JsonAlias({"vectorStore", "vector_store"})
        private String vectorStoreName;
        private Integer topK;
        private Double similarityThreshold;
        private String filterExpression;
        private String promptTemplate;
    }

    @Data
    public static class LoggerConfig {
        private Boolean enabled;
        private Integer order;
    }
}
