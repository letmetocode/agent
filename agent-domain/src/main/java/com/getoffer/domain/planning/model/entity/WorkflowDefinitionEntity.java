package com.getoffer.domain.planning.model.entity;

import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Workflow Definition 领域实体。
 */
@Data
public class WorkflowDefinitionEntity {

    private Long id;
    private String definitionKey;
    private String tenantId;
    private String category;
    private String name;
    private Integer version;
    private String routeDescription;
    private Map<String, Object> graphDefinition;
    private Map<String, Object> inputSchema;
    private Map<String, Object> defaultConfig;
    private Map<String, Object> toolPolicy;
    private String inputSchemaVersion;
    private Map<String, Object> constraints;
    private String nodeSignature;
    private WorkflowDefinitionStatusEnum status;
    private Long publishedFromDraftId;
    private Boolean isActive;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void validate() {
        if (definitionKey == null || definitionKey.trim().isEmpty()) {
            throw new IllegalStateException("Definition key cannot be empty");
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalStateException("Tenant id cannot be empty");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalStateException("Category cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Name cannot be empty");
        }
        if (version == null || version < 1) {
            throw new IllegalStateException("Version must be greater than 0");
        }
        if (routeDescription == null || routeDescription.trim().isEmpty()) {
            throw new IllegalStateException("Route description cannot be empty");
        }
        if (graphDefinition == null || graphDefinition.isEmpty()) {
            throw new IllegalStateException("Graph definition cannot be empty");
        }
        if (status == null) {
            throw new IllegalStateException("Status cannot be null");
        }
    }
}

