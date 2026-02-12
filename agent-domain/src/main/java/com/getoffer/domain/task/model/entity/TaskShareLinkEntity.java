package com.getoffer.domain.task.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分享链接领域实体。
 */
@Data
public class TaskShareLinkEntity {

    private Long id;
    private Long taskId;
    private String shareCode;
    private String tokenHash;
    private String scope;
    private LocalDateTime expiresAt;
    private Boolean revoked;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private String createdBy;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 是否处于可访问状态。
     */
    public boolean isActive() {
        if (Boolean.TRUE.equals(revoked)) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(LocalDateTime.now());
    }
}
