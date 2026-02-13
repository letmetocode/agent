package com.getoffer.api.dto;

import lombok.Data;

import java.util.List;

/**
 * V3 会话历史响应。
 */
@Data
public class ChatHistoryResponseV3DTO {

    private Long sessionId;
    private String userId;
    private String title;
    private String agentKey;
    private String scenario;
    private Long latestPlanId;
    private List<SessionTurnDTO> turns;
    private List<SessionMessageDTO> messages;
}
