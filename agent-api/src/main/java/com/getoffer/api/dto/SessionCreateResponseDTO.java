package com.getoffer.api.dto;

import lombok.Data;

/**
 * 创建会话响应 DTO
 */
@Data
public class SessionCreateResponseDTO {

    private Long sessionId;

    private String userId;

    private String title;

    private Boolean active;
}
