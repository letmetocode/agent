package com.getoffer.api.dto;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息 DTO。
 */
@Data
public class SessionMessageDTO {

    private Long messageId;
    private Long sessionId;
    private Long turnId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
