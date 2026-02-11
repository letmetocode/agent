package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.TurnStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话回合 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTurnPO {

    private Long id;
    private Long sessionId;
    private Long planId;
    private String userMessage;
    private TurnStatusEnum status;
    private Long finalResponseMessageId;
    private String assistantSummary;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
