package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Workflow Draft 生命周期状态。
 */
public enum WorkflowDraftStatusEnum {

    /**
     * 草稿态。
     */
    DRAFT("draft"),

    /**
     * 评审态。
     */
    REVIEWING("reviewing"),

    /**
     * 已发布态。
     */
    PUBLISHED("published"),

    /**
     * 归档态。
     */
    ARCHIVED("archived");

    private final String code;

    WorkflowDraftStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static WorkflowDraftStatusEnum fromText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (WorkflowDraftStatusEnum value : WorkflowDraftStatusEnum.values()) {
            if (value.code.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown workflow draft status: " + text);
    }
}

