package com.getoffer.api.dto;

import lombok.Data;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户消息
     */
    private String message;
}
