package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录响应 DTO。
 */
@Data
public class AuthLoginResponseDTO {

    private String userId;
    private String displayName;
    private String token;
    private String tokenType;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String jti;
}
