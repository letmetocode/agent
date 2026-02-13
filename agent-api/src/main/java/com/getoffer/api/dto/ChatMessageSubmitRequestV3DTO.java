package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * V3 聊天消息提交请求。
 */
@Data
public class ChatMessageSubmitRequestV3DTO {

    private String userId;
    private Long sessionId;
    private String message;
    private String title;
    private String agentKey;
    private String scenario;
    private Map<String, Object> metaInfo;
    private Map<String, Object> contextOverrides;
}
