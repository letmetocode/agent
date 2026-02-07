package com.getoffer.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建会话请求 DTO
 */
@Data
public class SessionCreateRequestDTO {

    /**
     * 外部用户 ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 扩展信息
     */
    private Map<String, Object> metaInfo;
}
