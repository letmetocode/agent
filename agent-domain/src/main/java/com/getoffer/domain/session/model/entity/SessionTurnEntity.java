package com.getoffer.domain.session.model.entity;

import com.getoffer.types.enums.TurnStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话回合实体。
 */
@Data
public class SessionTurnEntity {

    private Long id;
    private Long sessionId;
    private Long planId;
    private String userMessage;
    private TurnStatusEnum status;
    private Long finalResponseMessageId;
    private String assistantSummary;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public void validate() {
        if (sessionId == null) {
            throw new IllegalStateException("Session ID cannot be null");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalStateException("User message cannot be empty");
        }
        if (status == null) {
            throw new IllegalStateException("Turn status cannot be null");
        }
    }

    public boolean isTerminal() {
        return status == TurnStatusEnum.COMPLETED
                || status == TurnStatusEnum.FAILED
                || status == TurnStatusEnum.CANCELLED;
    }
}
