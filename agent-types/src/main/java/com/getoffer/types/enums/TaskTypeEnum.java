package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 任务类型枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum TaskTypeEnum {

    /**
     * Worker 任务 - 执行具体的工作任务
     */
    WORKER("worker"),

    /**
     * Critic 任务 - 验证和评价 Worker 任务的结果
     */
    CRITIC("critic");

    private final String code;

    TaskTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static TaskTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TaskTypeEnum type : TaskTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown task type code: " + code);
    }
}
