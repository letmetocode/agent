package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Workflow Definition PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionPO {

    private Long id;
    private String definitionKey;
    private String tenantId;
    private String category;
    private String name;
    private Integer version;
    private String routeDescription;
    private String graphDefinition;
    private String inputSchema;
    private String defaultConfig;
    private String toolPolicy;
    private String inputSchemaVersion;
    private String constraints;
    private String nodeSignature;
    private WorkflowDefinitionStatusEnum status;
    private Long publishedFromDraftId;
    private Boolean isActive;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

