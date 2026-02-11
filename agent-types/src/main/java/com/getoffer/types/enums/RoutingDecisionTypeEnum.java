package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 路由决策类型。
 */
public enum RoutingDecisionTypeEnum {

    /**
     * 命中生产 Definition。
     */
    HIT_PRODUCTION("hit_production"),

    /**
     * 未命中生产，命中/生成候选 Draft。
     */
    CANDIDATE("candidate"),

    /**
     * Root 失败后兜底 Draft。
     */
    FALLBACK("fallback");

    private final String code;

    RoutingDecisionTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static RoutingDecisionTypeEnum fromText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (RoutingDecisionTypeEnum value : RoutingDecisionTypeEnum.values()) {
            if (value.code.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown routing decision type: " + text);
    }
}

