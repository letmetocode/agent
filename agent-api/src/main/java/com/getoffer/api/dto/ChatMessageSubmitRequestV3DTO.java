package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * V3 聊天消息提交请求。
 */
@Data
public class ChatMessageSubmitRequestV3DTO {

    /**
     * 客户端提交幂等键，建议前端每次点击发送生成唯一值。
     */
    private String clientMessageId;
    private String userId;
    private Long sessionId;
    private String message;
    private String title;
    private String agentKey;
    private String scenario;
    private Map<String, Object> metaInfo;
    private Map<String, Object> contextOverrides;
}
