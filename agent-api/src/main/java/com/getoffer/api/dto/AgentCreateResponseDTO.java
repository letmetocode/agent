package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 创建响应 DTO（V2）。
 */
@Data
public class AgentCreateResponseDTO {

    private Long agentId;
    private String agentKey;
    private String name;
    private String modelProvider;
    private String modelName;
    private Boolean active;
    private LocalDateTime createdAt;
}
