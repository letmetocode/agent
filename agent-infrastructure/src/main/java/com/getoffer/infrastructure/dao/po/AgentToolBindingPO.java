package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.ToolTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 工具绑定聚合查询 PO（relation + catalog）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolBindingPO {

    private Long relationId;
    private Long agentId;
    private Long toolId;
    private Boolean relationEnabled;
    private Integer priority;
    private LocalDateTime relationCreatedAt;

    private String toolName;
    private ToolTypeEnum toolType;
    private String description;
    private String toolConfig;
    private String inputSchema;
    private String outputSchema;
    private Boolean toolActive;
    private LocalDateTime toolCreatedAt;
    private LocalDateTime toolUpdatedAt;
}
