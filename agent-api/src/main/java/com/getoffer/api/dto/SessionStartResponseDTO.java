package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话启动响应 DTO（V2）。
 */
@Data
public class SessionStartResponseDTO {

    private Long sessionId;
    private String userId;
    private String title;
    private String agentKey;
    private String scenario;
    private Boolean active;
    private LocalDateTime createdAt;
}
