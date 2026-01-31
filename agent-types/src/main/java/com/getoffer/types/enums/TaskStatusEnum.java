package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 任务状态枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum TaskStatusEnum {

    /**
     * 待处理 - 任务已创建，等待前置依赖完成
     */
    PENDING("pending"),

    /**
     * 就绪 - 前置依赖已完成，可以执行
     */
    READY("ready"),

    /**
     * 运行中 - 任务正在执行
     */
    RUNNING("running"),

    /**
     * 验证中 - 输出正在被 Critic 验证
     */
    VALIDATING("validating"),

    /**
     * 优化中 - 正在根据反馈重新执行
     */
    REFINING("refining"),

    /**
     * 已完成 - 任务成功完成并通过验证
     */
    COMPLETED("completed"),

    /**
     * 失败 - 任务执行失败或验证失败且达到最大重试次数
     */
    FAILED("failed"),

    /**
     * 已跳过 - 任务因前置任务失败而被跳过
     */
    SKIPPED("skipped");

    private final String code;

    TaskStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static TaskStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TaskStatusEnum status : TaskStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status code: " + code);
    }
}
