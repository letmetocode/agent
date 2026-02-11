package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.MessageRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessagePO {

    private Long id;
    private Long sessionId;
    private Long turnId;
    private MessageRoleEnum role;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;
}
