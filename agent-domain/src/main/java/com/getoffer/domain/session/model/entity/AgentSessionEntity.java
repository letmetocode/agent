package com.getoffer.domain.session.model.entity;

import com.getoffer.types.enums.ResponseCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户会话领域实体
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
public class AgentSessionEntity {

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
     * 会话绑定 Agent Key（可空，兼容旧数据）
     */
    private String agentKey;

    /**
     * 场景标识（可空）
     */
    private String scenario;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 扩展字段 (解析后的 Map，存租户ID等)
     */
    private Map<String, Object> metaInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 验证会话是否有效
     */
    public void validate() {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalStateException("User ID cannot be empty");
        }
    }

    /**
     * 激活会话
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 关闭会话
     */
    public void close() {
        this.isActive = false;
    }

    /**
     * 更新会话标题
     */
    public void updateTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title;
        }
    }

    /**
     * 更新元信息
     */
    public void updateMetaInfo(Map<String, Object> metaInfo) {
        this.metaInfo = metaInfo;
    }

    /**
     * 检查会话是否活跃
     */
    public boolean isSessionActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
}
