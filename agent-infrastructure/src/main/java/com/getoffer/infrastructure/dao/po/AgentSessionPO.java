package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户会话 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 外部用户 ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 扩展字段 (JSONB，存租户ID等)
     */
    private String metaInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
