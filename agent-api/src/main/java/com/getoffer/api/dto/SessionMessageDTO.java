package com.getoffer.api.dto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

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
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
