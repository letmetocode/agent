package com.getoffer.api.dto;

import lombok.Data;

/**
 * Agent 摘要 DTO（V2）。
 */
@Data
public class AgentSummaryDTO {

    private Long agentId;
    private String agentKey;
    private String name;
    private String modelProvider;
    private String modelName;
    private Boolean active;
}
