package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 回合创建请求 DTO（V2）。
 */
@Data
public class TurnCreateRequestDTO {

    private String message;
    private Map<String, Object> contextOverrides;
}
