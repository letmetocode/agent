package com.getoffer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前登录态信息 DTO。
 */
@Data
public class AuthMeResponseDTO {

    private String userId;
    private String displayName;
    private Boolean isOperator;
    private LocalDateTime lastLoginAt;
}
