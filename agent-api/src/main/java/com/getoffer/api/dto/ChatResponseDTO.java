package com.getoffer.api.dto;

import lombok.Data;

/**
 * 聊天响应 DTO
 */
@Data
public class ChatResponseDTO {

    private Long sessionId;

    private Long planId;

    private String planGoal;
}
