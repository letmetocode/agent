package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Workflow Definition 生命周期状态。
 */
public enum WorkflowDefinitionStatusEnum {

    /**
     * 启用态，允许参与生产路由。
     */
    ACTIVE("active"),

    /**
     * 禁用态，不参与生产路由。
     */
    DISABLED("disabled"),

    /**
     * 归档态，仅审计与回放。
     */
    ARCHIVED("archived");

    private final String code;

    WorkflowDefinitionStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static WorkflowDefinitionStatusEnum fromText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (WorkflowDefinitionStatusEnum value : WorkflowDefinitionStatusEnum.values()) {
            if (value.code.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown workflow definition status: " + text);
    }
}

