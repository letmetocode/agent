package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Workflow Draft PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDraftPO {

    private Long id;
    private String draftKey;
    private String tenantId;
    private String category;
    private String name;
    private String routeDescription;
    private String graphDefinition;
    private String inputSchema;
    private String defaultConfig;
    private String toolPolicy;
    private String inputSchemaVersion;
    private String constraints;
    private String nodeSignature;
    private String dedupHash;
    private String sourceType;
    private Long sourceDefinitionId;
    private WorkflowDraftStatusEnum status;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

