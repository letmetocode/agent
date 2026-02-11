package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话详情 DTO。
 */
@Data
public class SessionDetailDTO {

    private Long sessionId;
    private String userId;
    private String title;
    private Boolean active;
    private Map<String, Object> metaInfo;
    private LocalDateTime createdAt;
}
