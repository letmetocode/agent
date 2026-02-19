package com.getoffer.domain.session.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录态吊销黑名单实体（按 JWT jti 维度）。
 */
@Data
public class AuthSessionBlacklistEntity {

    private Long id;
    private String jti;
    private String userId;
    private LocalDateTime expiredAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private LocalDateTime createdAt;
}
