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
     * Spring Bean 工具 - 本地 Bean 方法
     */
    SPRING_BEAN("spring_bean"),

    /**
     * MCP 工具 - 通过 MCP Server 调用
     */
    MCP_FUNCTION("mcp_function"),

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
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tool type code: " + code);
    }
}
