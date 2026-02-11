package com.getoffer.domain.session.model.entity;

import com.getoffer.types.enums.MessageRoleEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话消息实体。
 */
@Data
public class SessionMessageEntity {

    private Long id;
    private Long sessionId;
    private Long turnId;
    private MessageRoleEnum role;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public void validate() {
        if (sessionId == null) {
            throw new IllegalStateException("Session ID cannot be null");
        }
        if (turnId == null) {
            throw new IllegalStateException("Turn ID cannot be null");
        }
        if (role == null) {
            throw new IllegalStateException("Message role cannot be null");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("Message content cannot be empty");
        }
    }
}
