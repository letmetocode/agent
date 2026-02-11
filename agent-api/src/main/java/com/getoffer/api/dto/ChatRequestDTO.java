package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户消息
     */
    private String message;

    /**
     * 额外上下文（可选），会合入本轮 Plan 全局上下文。
     */
    private Map<String, Object> extraContext;
}
