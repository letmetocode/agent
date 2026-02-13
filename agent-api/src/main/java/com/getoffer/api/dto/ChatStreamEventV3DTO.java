package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * V3 聊天流事件。
 */
@Data
public class ChatStreamEventV3DTO {

    private String type;
    private Long eventId;
    private Long sessionId;
    private Long planId;
    private Long turnId;
    private Long taskId;
    private String taskStatus;
    private String message;
    private String finalAnswer;
    private Map<String, Object> metadata;
}
