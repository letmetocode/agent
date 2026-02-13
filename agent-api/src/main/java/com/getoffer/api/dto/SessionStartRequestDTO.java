package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 会话启动请求 DTO（V2）。
 */
@Data
public class SessionStartRequestDTO {

    private String userId;
    private String title;
    private String agentKey;
    private String scenario;
    private Map<String, Object> metaInfo;
}
