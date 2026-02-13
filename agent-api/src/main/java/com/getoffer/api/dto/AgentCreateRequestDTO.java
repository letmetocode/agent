package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * Agent 创建请求 DTO（V2）。
 */
@Data
public class AgentCreateRequestDTO {

    private String agentKey;
    private String name;
    private String modelProvider;
    private String modelName;
    private String baseSystemPrompt;
    private Boolean active;
    private Map<String, Object> modelOptions;
    private Map<String, Object> advisorConfig;
}
