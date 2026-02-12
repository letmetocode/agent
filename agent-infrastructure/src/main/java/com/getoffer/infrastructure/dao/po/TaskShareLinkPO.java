package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务分享链接 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskShareLinkPO {

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
}
