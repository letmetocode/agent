package com.getoffer.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JWT 登录态黑名单 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionBlacklistPO {

    private Long id;
    private String jti;
    private String userId;
    private LocalDateTime expiredAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private LocalDateTime createdAt;
}
