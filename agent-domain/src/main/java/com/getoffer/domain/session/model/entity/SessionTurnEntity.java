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

    public void startPlanning() {
        if (status != null) {
            throw new IllegalStateException("Turn status already initialized");
        }
        this.status = TurnStatusEnum.PLANNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markExecuting(Long planId) {
        if (isTerminal()) {
            throw new IllegalStateException("Terminal turn cannot enter EXECUTING");
        }
        this.planId = planId;
        this.status = TurnStatusEnum.EXECUTING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompleted(String summary, Long messageId, LocalDateTime completedAt) {
        if (isTerminal() && this.status != TurnStatusEnum.COMPLETED) {
            throw new IllegalStateException("Terminal turn cannot become COMPLETED");
        }
        this.status = TurnStatusEnum.COMPLETED;
        this.assistantSummary = summary;
        this.finalResponseMessageId = messageId;
        this.completedAt = completedAt == null ? LocalDateTime.now() : completedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String summary, LocalDateTime completedAt) {
        if (isTerminal() && this.status != TurnStatusEnum.FAILED) {
            throw new IllegalStateException("Terminal turn cannot become FAILED");
        }
        this.status = TurnStatusEnum.FAILED;
        this.assistantSummary = summary;
        this.completedAt = completedAt == null ? LocalDateTime.now() : completedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void bindFinalResponseMessage(Long messageId) {
        if (messageId == null) {
            throw new IllegalStateException("Final response message id cannot be null");
        }
        this.finalResponseMessageId = messageId;
        this.updatedAt = LocalDateTime.now();
    }
}
