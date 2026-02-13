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

    public boolean isAssistantMessage() {
        return role == MessageRoleEnum.ASSISTANT;
    }

    public boolean isUserMessage() {
        return role == MessageRoleEnum.USER;
    }

    public void updateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("Message content cannot be empty");
        }
        this.content = content.trim();
    }

    public static SessionMessageEntity userMessage(Long sessionId, Long turnId, String content) {
        return create(sessionId, turnId, MessageRoleEnum.USER, content);
    }

    public static SessionMessageEntity assistantMessage(Long sessionId, Long turnId, String content) {
        return create(sessionId, turnId, MessageRoleEnum.ASSISTANT, content);
    }

    private static SessionMessageEntity create(Long sessionId,
                                               Long turnId,
                                               MessageRoleEnum role,
                                               String content) {
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setSessionId(sessionId);
        entity.setTurnId(turnId);
        entity.setRole(role);
        entity.updateContent(content);
        return entity;
    }
}
