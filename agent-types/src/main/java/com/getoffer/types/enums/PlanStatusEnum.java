package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 执行计划状态枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum PlanStatusEnum {

    /**
     * 规划中 - 正在创建执行计划
     */
    PLANNING("planning"),

    /**
     * 就绪 - 计划已创建，等待执行
     */
    READY("ready"),

    /**
     * 运行中 - 任务正在执行
     */
    RUNNING("running"),

    /**
     * 暂停 - 执行已暂停，可恢复
     */
    PAUSED("paused"),

    /**
     * 已完成 - 所有任务成功执行
     */
    COMPLETED("completed"),

    /**
     * 失败 - 执行过程中出现错误
     */
    FAILED("failed"),

    /**
     * 已取消 - 用户取消执行
     */
    CANCELLED("cancelled");

    private final String code;

    PlanStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static PlanStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PlanStatusEnum status : PlanStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown plan status code: " + code);
    }
}
