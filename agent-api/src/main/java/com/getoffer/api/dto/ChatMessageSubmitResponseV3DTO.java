package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * V3 聊天消息提交响应。
 */
@Data
public class ChatMessageSubmitResponseV3DTO {

    private Long sessionId;
    private Long turnId;
    private Long planId;
    private String turnStatus;
    private Boolean accepted;
    private String submissionState;
    private LocalDateTime acceptedAt;
    private String sessionTitle;
    private String assistantMessage;
    private String streamPath;
    private String historyPath;
    private RoutingDecisionDTO routingDecision;
}
