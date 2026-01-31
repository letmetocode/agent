package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * SOP 结构类型枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum SopStructureEnum {

    /**
     * 链式结构 - 按顺序依次执行
     */
    CHAIN("chain"),

    /**
     * DAG 结构 - 支持并行执行和依赖关系
     */
    DAG("dag");

    private final String code;

    SopStructureEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static SopStructureEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SopStructureEnum structure : SopStructureEnum.values()) {
            if (structure.code.equals(code)) {
                return structure;
            }
        }
        throw new IllegalArgumentException("Unknown SOP structure code: " + code);
    }
}
