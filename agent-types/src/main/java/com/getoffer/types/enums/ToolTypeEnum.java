package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 工具类型枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum ToolTypeEnum {

    /**
     * 函数 - 编程函数或方法
     */
    FUNCTION("function"),

    /**
     * API - HTTP/REST API 端点
     */
    API("api"),

    /**
     * 插件 - 外部插件或扩展
     */
    PLUGIN("plugin");

    private final String code;

    ToolTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static ToolTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ToolTypeEnum type : ToolTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tool type code: " + code);
    }
}
