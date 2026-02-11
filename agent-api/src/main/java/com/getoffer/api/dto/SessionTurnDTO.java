package com.getoffer.api.dto;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话回合 DTO。
 */
@Data
public class SessionTurnDTO {

    private Long turnId;
    private Long sessionId;
    private Long planId;
    private String userMessage;
    private String status;
    private Long finalResponseMessageId;
    private String assistantSummary;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
